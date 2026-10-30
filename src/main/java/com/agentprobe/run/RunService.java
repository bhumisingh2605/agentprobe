package com.agentprobe.run;

import com.agentprobe.adapter.AgentTarget;
import com.agentprobe.agent.AgentService;
import com.agentprobe.api.ApiException;
import com.agentprobe.run.RunDtos.EvaluationDto;
import com.agentprobe.run.RunDtos.RunDetail;
import com.agentprobe.run.RunDtos.RunSummary;
import com.agentprobe.run.RunDtos.ScenarioResultDto;
import com.agentprobe.run.RunDtos.StartRunRequest;
import com.agentprobe.run.RunDtos.TurnDto;
import com.agentprobe.suite.Scenario;
import com.agentprobe.suite.SuiteService;
import com.agentprobe.util.Json;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Not @Transactional on purpose: nothing here may hold a DB transaction while agents are called. */
@Service
public class RunService {

    private static final int MAX_SCENARIOS = 100;

    private final SuiteService suiteService;
    private final AgentService agentService;
    private final RunExecutor executor;
    private final RunEvents events;
    private final TestRunRepository runs;
    private final ScenarioResultRepository results;
    private final EvaluationRecordRepository evaluations;
    private final TurnRecordRepository turnRepo;

    public RunService(SuiteService suiteService, AgentService agentService, RunExecutor executor,
                      RunEvents events, TestRunRepository runs, ScenarioResultRepository results,
                      EvaluationRecordRepository evaluations, TurnRecordRepository turnRepo) {
        this.suiteService = suiteService;
        this.agentService = agentService;
        this.executor = executor;
        this.events = events;
        this.runs = runs;
        this.results = results;
        this.evaluations = evaluations;
        this.turnRepo = turnRepo;
    }

    public RunSummary start(UUID projectId, UUID suiteId, StartRunRequest req, UUID userId) {
        List<Scenario> scenarioList = suiteService.scenariosForRun(projectId, suiteId, userId);
        if (scenarioList.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This suite has no scenarios");
        }
        if (scenarioList.size() > MAX_SCENARIOS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A run can have at most " + MAX_SCENARIOS + " scenarios");
        }
        AgentTarget target = agentService.resolveTarget(projectId, req.agentId(), req.versionId(), userId);

        int concurrency = req.concurrency() == null ? 3 : req.concurrency();
        TestRun run = runs.save(new TestRun(projectId, suiteId, req.versionId(), scenarioList.size(),
                concurrency, req.tokenBudget()));
        try {
            executor.submit(run, scenarioList, target);
        } catch (ApiException e) {
            run.end("FAILED", e.getMessage(), 0, 0, 0, 0);
            runs.save(run);
            throw e;
        }
        return RunSummary.from(run);
    }

    public List<RunSummary> list(UUID projectId, UUID suiteId, UUID userId) {
        suiteService.requireSuite(projectId, suiteId, userId);
        List<RunSummary> out = new ArrayList<RunSummary>();
        for (TestRun r : runs.findAllByProjectIdAndSuiteIdOrderByCreatedAtDesc(projectId, suiteId)) {
            out.add(RunSummary.from(r));
        }
        return out;
    }

    public RunDetail get(UUID projectId, UUID runId, UUID userId) {
        return detail(findRun(projectId, runId, userId));
    }

    public RunSummary cancel(UUID projectId, UUID runId, UUID userId) {
        TestRun run = findRun(projectId, runId, userId);
        if (!run.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "Run already finished (" + run.getStatus() + ")");
        }
        if (!executor.requestCancel(runId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Run is not active on this server");
        }
        return RunSummary.from(run);
    }

    public SseEmitter subscribe(UUID projectId, UUID runId, UUID userId) {
        TestRun run = findRun(projectId, runId, userId);
        SseEmitter emitter = events.subscribe(runId, snapshot(run));
        // The run may have finished between the read above and subscribing: close the stream in that case.
        TestRun fresh = runs.findById(runId).orElse(run);
        if (!fresh.isActive()) {
            events.publish(runId, "done", snapshot(fresh));
            events.complete(runId);
        }
        return emitter;
    }

    // ------------------------------------------------------------------ internals

    private TestRun findRun(UUID projectId, UUID runId, UUID userId) {
        suiteService.requireProject(projectId, userId);
        return runs.findByIdAndProjectId(runId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Run not found"));
    }

    private static Map<String, Object> snapshot(TestRun r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("runId", r.getId().toString());
        m.put("status", r.getStatus());
        m.put("total", r.getTotalScenarios());
        m.put("completed", r.getPassedCount() + r.getFailedCount());
        m.put("passed", r.getPassedCount());
        m.put("failed", r.getFailedCount());
        m.put("error", r.getError());
        return m;
    }

    private RunDetail detail(TestRun run) {
        List<ScenarioResult> resultList = results.findAllByRunIdOrderByPositionAsc(run.getId());
        List<UUID> ids = new ArrayList<UUID>();
        for (ScenarioResult r : resultList) {
            ids.add(r.getId());
        }

        long judgeTokens = 0;
        Map<UUID, List<EvaluationDto>> evalsByResult = new HashMap<UUID, List<EvaluationDto>>();
        Map<UUID, List<TurnDto>> turnsByResult = new HashMap<UUID, List<TurnDto>>();
        if (!ids.isEmpty()) {
            for (EvaluationRecord e : evaluations.findAllByScenarioResultIdIn(ids)) {
                if (e.getJudgeTokens() != null) {
                    judgeTokens += e.getJudgeTokens();
                }
                evalsByResult.computeIfAbsent(e.getScenarioResultId(), k -> new ArrayList<EvaluationDto>())
                        .add(new EvaluationDto(e.getEvaluatorType(), e.isPassed(), e.getScore(), e.getWeight(),
                                e.getReasoning(), e.getJudgeModel(), e.getJudgePromptVersion(),
                                e.getJudgeTokens(), e.getJudgeLatencyMs()));
            }
            for (TurnRecord t : turnRepo.findAllByScenarioResultIdInOrderByTurnNumberAsc(ids)) {
                turnsByResult.computeIfAbsent(t.getScenarioResultId(), k -> new ArrayList<TurnDto>())
                        .add(new TurnDto(t.getTurnNumber(), t.getRole(), t.getContent(),
                                Json.list(t.getToolCalls()), t.getLatencyMs()));
            }
        }

        List<ScenarioResultDto> dtos = new ArrayList<ScenarioResultDto>();
        for (ScenarioResult r : resultList) {
            List<EvaluationDto> evalDtos = evalsByResult.get(r.getId());
            List<TurnDto> turnDtos = turnsByResult.get(r.getId());
            dtos.add(new ScenarioResultDto(r.getId(), r.getExternalId(), r.isPassed(), r.getScore(),
                    r.getLatencyMs(), r.getTokens(), r.getHttpStatus(), r.getUserMessage(), r.getAgentAnswer(),
                    Json.list(r.getToolCalls()), r.getError(), r.getStopReason(), r.getTurnCount(),
                    turnDtos == null ? new ArrayList<TurnDto>() : turnDtos,
                    evalDtos == null ? new ArrayList<EvaluationDto>() : evalDtos));
        }
        return new RunDetail(RunSummary.from(run), judgeTokens, dtos);
    }
}
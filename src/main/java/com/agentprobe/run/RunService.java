package com.agentprobe.run;

import com.agentprobe.adapter.AgentAdapter;
import com.agentprobe.adapter.AgentReply;
import com.agentprobe.adapter.AgentTarget;
import com.agentprobe.agent.AgentService;
import com.agentprobe.api.ApiException;
import com.agentprobe.evaluation.EvaluationResult;
import com.agentprobe.evaluation.Evaluator;
import com.agentprobe.evaluation.EvaluatorRegistry;
import com.agentprobe.evaluation.Params;
import com.agentprobe.evaluation.ScenarioContext;
import com.agentprobe.run.RunDtos.EvaluationDto;
import com.agentprobe.run.RunDtos.RunDetail;
import com.agentprobe.run.RunDtos.RunSummary;
import com.agentprobe.run.RunDtos.ScenarioResultDto;
import com.agentprobe.suite.Scenario;
import com.agentprobe.suite.SuiteService;
import com.agentprobe.util.Json;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronous run engine (Week 4 replaces it with a queue and workers).
 * Not @Transactional on purpose: agent and judge calls are slow and must not hold a DB transaction.
 */
@Service
public class RunService {

    private static final int MAX_SCENARIOS = 100;

    private final SuiteService suiteService;
    private final AgentService agentService;
    private final AgentAdapter adapter;
    private final EvaluatorRegistry registry;
    private final TestRunRepository runs;
    private final ScenarioResultRepository results;
    private final EvaluationRecordRepository evaluations;

    public RunService(SuiteService suiteService, AgentService agentService, AgentAdapter adapter,
                      EvaluatorRegistry registry, TestRunRepository runs, ScenarioResultRepository results,
                      EvaluationRecordRepository evaluations) {
        this.suiteService = suiteService;
        this.agentService = agentService;
        this.adapter = adapter;
        this.registry = registry;
        this.runs = runs;
        this.results = results;
        this.evaluations = evaluations;
    }

    public RunDetail start(UUID projectId, UUID suiteId, UUID agentId, UUID versionId, UUID userId) {
        List<Scenario> scenarioList = suiteService.scenariosForRun(projectId, suiteId, userId);
        if (scenarioList.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This suite has no scenarios");
        }
        if (scenarioList.size() > MAX_SCENARIOS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A run can have at most " + MAX_SCENARIOS + " scenarios");
        }
        AgentTarget target = agentService.resolveTarget(projectId, agentId, versionId, userId);

        TestRun run = runs.save(new TestRun(projectId, suiteId, versionId, scenarioList.size()));
        try {
            int passed = 0;
            int failed = 0;
            long latencySum = 0;
            int latencyCount = 0;
            long tokens = 0;
            int position = 0;

            for (Scenario s : scenarioList) {
                ScenarioResult r = runOne(run, s, target, position++);
                if (r.isPassed()) {
                    passed++;
                } else {
                    failed++;
                }
                if (r.getLatencyMs() > 0) {
                    latencySum += r.getLatencyMs();
                    latencyCount++;
                }
                if (r.getTokens() != null) {
                    tokens += r.getTokens();
                }
            }
            run.finish(passed, failed, latencyCount == 0 ? 0 : latencySum / latencyCount, tokens);
        } catch (RuntimeException e) {
            run.markFailed();
            runs.save(run);
            throw e;
        }
        runs.save(run);
        return detail(run);
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
        suiteService.requireProject(projectId, userId);
        TestRun run = runs.findByIdAndProjectId(runId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Run not found"));
        return detail(run);
    }

    // ------------------------------------------------------------------ internals

    private record Weighted(EvaluationResult result, double weight) {
    }

    private ScenarioResult runOne(TestRun run, Scenario s, AgentTarget target, int position) {
        ScenarioResult r = new ScenarioResult(run.getId(), s.getId(), s.getExternalId(), position);
        List<Weighted> evals = new ArrayList<Weighted>();

        if (!"single_turn".equals(s.getType())) {
            r.fail("multi_turn scenarios can be run from Week 4");
        } else {
            r.setUserMessage(s.getInput());
            AgentReply reply = null;
            try {
                String session = "run-" + run.getId().toString().substring(0, 8) + "-" + position;
                reply = adapter.send(target, s.getInput(), session);
            } catch (ApiException e) {
                r.fail(e.getMessage());
            }
            if (reply != null) {
                r.setLatencyMs(reply.latencyMs());
                r.setHttpStatus(reply.httpStatus());
                r.setTokens(reply.tokens());
                r.setAgentAnswer(reply.answer());
                r.setToolCalls(Json.write(reply.toolCalls()));

                if (reply.httpStatus() < 200 || reply.httpStatus() >= 300) {
                    r.fail("Agent returned HTTP " + reply.httpStatus());
                } else if (reply.answer() == null) {
                    r.fail("Could not find the answer in the response. Check the responseMapping answerPath.");
                } else {
                    ScenarioContext ctx = new ScenarioContext(s.getInput(), reply.answer(), reply.toolCalls(),
                            reply.tokens(), reply.latencyMs(), reply.httpStatus());
                    evaluate(s, ctx, evals);
                    score(r, evals);
                }
            }
        }

        ScenarioResult saved = results.save(r);
        List<EvaluationRecord> records = new ArrayList<EvaluationRecord>();
        for (Weighted w : evals) {
            records.add(new EvaluationRecord(saved.getId(), w.result(), w.weight()));
        }
        evaluations.saveAll(records);
        return saved;
    }

    @SuppressWarnings("unchecked")
    private void evaluate(Scenario s, ScenarioContext ctx, List<Weighted> out) {
        for (Object o : Json.list(s.getEvaluators())) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> cfg = (Map<String, Object>) o;
            String type = String.valueOf(cfg.get("type"));
            double weight = Params.numberOr(cfg, "weight", 1.0);
            EvaluationResult result;
            Evaluator ev = registry.find(type).orElse(null);
            if (ev == null) {
                result = EvaluationResult.fail(type, "Unknown evaluator '" + type + "'");
            } else {
                try {
                    result = ev.evaluate(ctx, new HashMap<String, Object>(cfg));
                } catch (RuntimeException e) {
                    result = EvaluationResult.fail(type, "Evaluator crashed: " + e.getMessage());
                }
            }
            out.add(new Weighted(result, weight));
        }
    }

    /** Scenario passes only if every evaluator passes. Score is the weighted average of evaluator scores. */
    private static void score(ScenarioResult r, List<Weighted> evals) {
        if (evals.isEmpty()) {
            r.setPassed(true);
            r.setScore(1.0);
            return;
        }
        double weightSum = 0;
        double scoreSum = 0;
        boolean allPassed = true;
        for (Weighted w : evals) {
            weightSum += w.weight();
            scoreSum += w.result().score() * w.weight();
            if (!w.result().passed()) {
                allPassed = false;
            }
        }
        r.setPassed(allPassed);
        r.setScore(weightSum == 0 ? 0.0 : scoreSum / weightSum);
    }

    private RunDetail detail(TestRun run) {
        List<ScenarioResult> resultList = results.findAllByRunIdOrderByPositionAsc(run.getId());
        List<UUID> ids = new ArrayList<UUID>();
        for (ScenarioResult r : resultList) {
            ids.add(r.getId());
        }
        long judgeTokens = 0;
        Map<UUID, List<EvaluationDto>> byResult = new HashMap<UUID, List<EvaluationDto>>();
        if (!ids.isEmpty()) {
            for (EvaluationRecord e : evaluations.findAllByScenarioResultIdIn(ids)) {
                if (e.getJudgeTokens() != null) {
                    judgeTokens += e.getJudgeTokens();
                }
                byResult.computeIfAbsent(e.getScenarioResultId(), k -> new ArrayList<EvaluationDto>())
                        .add(new EvaluationDto(e.getEvaluatorType(), e.isPassed(), e.getScore(), e.getWeight(),
                                e.getReasoning(), e.getJudgeModel(), e.getJudgePromptVersion(),
                                e.getJudgeTokens(), e.getJudgeLatencyMs()));
            }
        }
        List<ScenarioResultDto> dtos = new ArrayList<ScenarioResultDto>();
        for (ScenarioResult r : resultList) {
            List<EvaluationDto> evalDtos = byResult.get(r.getId());
            dtos.add(new ScenarioResultDto(r.getId(), r.getExternalId(), r.isPassed(), r.getScore(),
                    r.getLatencyMs(), r.getTokens(), r.getHttpStatus(), r.getUserMessage(), r.getAgentAnswer(),
                    Json.list(r.getToolCalls()), r.getError(),
                    evalDtos == null ? new ArrayList<EvaluationDto>() : evalDtos));
        }
        return new RunDetail(RunSummary.from(run), judgeTokens, dtos);
    }
}
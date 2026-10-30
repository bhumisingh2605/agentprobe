package com.agentprobe.run;

import com.agentprobe.agent.AgentVersion;
import com.agentprobe.agent.AgentVersionRepository;
import com.agentprobe.api.ApiException;
import com.agentprobe.regression.RunComparator;
import com.agentprobe.run.RegressionDtos.BaselineDto;
import com.agentprobe.run.RegressionDtos.ComparisonResponse;
import com.agentprobe.run.RegressionDtos.ThresholdDto;
import com.agentprobe.suite.SuiteService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RegressionService {

    private final SuiteService suiteService;
    private final TestRunRepository runs;
    private final ScenarioResultRepository results;
    private final BaselineRepository baselines;
    private final SuiteThresholdRepository thresholds;
    private final AgentVersionRepository versions;

    public RegressionService(SuiteService suiteService, TestRunRepository runs, ScenarioResultRepository results,
                             BaselineRepository baselines, SuiteThresholdRepository thresholds,
                             AgentVersionRepository versions) {
        this.suiteService = suiteService;
        this.runs = runs;
        this.results = results;
        this.baselines = baselines;
        this.thresholds = thresholds;
        this.versions = versions;
    }

    // ------------------------------------------------------------------ baselines

    @Transactional
    public BaselineDto markBaseline(UUID projectId, UUID runId, UUID userId) {
        TestRun run = findRun(projectId, runId, userId);
        if (!"COMPLETED".equals(run.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Only a COMPLETED run can be the baseline (this run is " + run.getStatus() + ")");
        }
        UUID agentId = agentIdOf(run);
        Baseline b = baselines.findBySuiteIdAndAgentId(run.getSuiteId(), agentId).orElse(null);
        if (b == null) {
            b = new Baseline(run.getSuiteId(), agentId, run.getId());
        } else {
            b.changeRun(run.getId());
        }
        return toDto(baselines.save(b));
    }

    @Transactional(readOnly = true)
    public List<BaselineDto> listBaselines(UUID projectId, UUID suiteId, UUID userId) {
        suiteService.requireSuite(projectId, suiteId, userId);
        List<BaselineDto> out = new ArrayList<BaselineDto>();
        for (Baseline b : baselines.findAllBySuiteId(suiteId)) {
            out.add(toDto(b));
        }
        return out;
    }

    // ------------------------------------------------------------------ thresholds

    @Transactional(readOnly = true)
    public ThresholdDto getThresholds(UUID projectId, UUID suiteId, UUID userId) {
        suiteService.requireSuite(projectId, suiteId, userId);
        RunComparator.Thresholds t = thresholdsFor(suiteId);
        return new ThresholdDto(t.maxPassRateDrop(), t.scenarioPassRateDrop(), t.scoreDrop(),
                t.latencyIncrease(), t.tokenIncrease());
    }

    @Transactional
    public ThresholdDto putThresholds(UUID projectId, UUID suiteId, UUID userId, ThresholdDto dto) {
        suiteService.requireSuite(projectId, suiteId, userId);
        SuiteThreshold row = thresholds.findById(suiteId).orElse(null);
        if (row == null) {
            row = new SuiteThreshold(suiteId, dto.maxPassRateDrop(), dto.scenarioPassRateDrop(), dto.scoreDrop(),
                    dto.latencyIncrease(), dto.tokenIncrease());
        } else {
            row.set(dto.maxPassRateDrop(), dto.scenarioPassRateDrop(), dto.scoreDrop(),
                    dto.latencyIncrease(), dto.tokenIncrease());
        }
        thresholds.save(row);
        return dto;
    }

    // ------------------------------------------------------------------ comparison

    @Transactional(readOnly = true)
    public ComparisonResponse compare(UUID projectId, UUID baseRunId, UUID candidateRunId, UUID userId) {
        if (baseRunId.equals(candidateRunId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Baseline and candidate must be different runs");
        }
        TestRun base = findRun(projectId, baseRunId, userId);
        TestRun cand = findRun(projectId, candidateRunId, userId);
        if (!base.getSuiteId().equals(cand.getSuiteId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Both runs must belong to the same suite");
        }
        RunComparator.Comparison c = RunComparator.compare(data(base), data(cand), thresholdsFor(base.getSuiteId()));
        return new ComparisonResponse(base.getId(), cand.getId(), label(base), label(cand), c);
    }

    @Transactional(readOnly = true)
    public ComparisonResponse compareToBaseline(UUID projectId, UUID candidateRunId, UUID userId) {
        TestRun cand = findRun(projectId, candidateRunId, userId);
        UUID agentId = agentIdOf(cand);
        Baseline b = baselines.findBySuiteIdAndAgentId(cand.getSuiteId(), agentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No baseline set for this suite and agent. Mark a COMPLETED run as baseline first."));
        if (b.getRunId().equals(cand.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This run is the baseline itself");
        }
        return compare(projectId, b.getRunId(), cand.getId(), userId);
    }

    // ------------------------------------------------------------------ helpers

    private TestRun findRun(UUID projectId, UUID runId, UUID userId) {
        suiteService.requireProject(projectId, userId);
        return runs.findByIdAndProjectId(runId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Run not found"));
    }

    private UUID agentIdOf(TestRun run) {
        return versions.findById(run.getAgentVersionId())
                .map(AgentVersion::getAgentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "The agent version of this run was deleted"));
    }

    private String label(TestRun run) {
        return versions.findById(run.getAgentVersionId()).map(AgentVersion::getVersionLabel)
                .orElse("(deleted version)");
    }

    private RunComparator.RunData data(TestRun run) {
        List<RunComparator.Row> rows = new ArrayList<RunComparator.Row>();
        for (ScenarioResult r : results.findAllByRunIdOrderByPositionAsc(run.getId())) {
            rows.add(new RunComparator.Row(r.getExternalId(), r.isPassed(), r.getScore(),
                    r.getLatencyMs(), r.getTokens()));
        }
        return new RunComparator.RunData(run.getStatus(), rows);
    }

    private RunComparator.Thresholds thresholdsFor(UUID suiteId) {
        return thresholds.findById(suiteId)
                .map(t -> new RunComparator.Thresholds(t.getMaxPassRateDrop(), t.getScenarioPassRateDrop(),
                        t.getScoreDrop(), t.getLatencyIncrease(), t.getTokenIncrease()))
                .orElseGet(RunComparator::defaults);
    }

    private static BaselineDto toDto(Baseline b) {
        return new BaselineDto(b.getId(), b.getSuiteId(), b.getAgentId(), b.getRunId(), b.getCreatedAt());
    }
}
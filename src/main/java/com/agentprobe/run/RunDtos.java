package com.agentprobe.run;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RunDtos {

    private RunDtos() {
    }

    public record StartRunRequest(@NotNull UUID agentId, @NotNull UUID versionId) {
    }

    public record RunSummary(
            UUID id,
            UUID suiteId,
            UUID agentVersionId,
            String status,
            int totalScenarios,
            int passedCount,
            int failedCount,
            double passRate,
            long avgLatencyMs,
            long totalTokens,
            Instant startedAt,
            Instant finishedAt) {

        static RunSummary from(TestRun r) {
            double rate = r.getTotalScenarios() == 0 ? 0.0 : (double) r.getPassedCount() / r.getTotalScenarios();
            return new RunSummary(r.getId(), r.getSuiteId(), r.getAgentVersionId(), r.getStatus(),
                    r.getTotalScenarios(), r.getPassedCount(), r.getFailedCount(), rate,
                    r.getAvgLatencyMs(), r.getTotalTokens(), r.getStartedAt(), r.getFinishedAt());
        }
    }

    public record EvaluationDto(
            String type,
            boolean passed,
            double score,
            double weight,
            String reasoning,
            String judgeModel,
            String judgePromptVersion,
            Integer judgeTokens,
            Long judgeLatencyMs) {
    }

    public record ScenarioResultDto(
            UUID id,
            String scenarioId,
            boolean passed,
            double score,
            long latencyMs,
            Integer tokens,
            int httpStatus,
            String userMessage,
            String agentAnswer,
            Object toolCalls,
            String error,
            List<EvaluationDto> evaluations) {
    }

    /** judgeTokens is an estimate (characters / 4) of tokens spent on judge calls in this run. */
    public record RunDetail(RunSummary run, long judgeTokens, List<ScenarioResultDto> results) {
    }
}
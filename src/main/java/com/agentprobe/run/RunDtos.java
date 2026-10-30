package com.agentprobe.run;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RunDtos {

    private RunDtos() {
    }

    /** concurrency: scenarios in parallel (default 3). tokenBudget: stop when agent+judge+simulator tokens (estimate) reach it. */
    public record StartRunRequest(
            @NotNull UUID agentId,
            @NotNull UUID versionId,
            @Min(1) @Max(8) Integer concurrency,
            @Min(1000) Integer tokenBudget) {
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
            int concurrency,
            Integer tokenBudget,
            String error,
            Instant startedAt,
            Instant finishedAt) {

        static RunSummary from(TestRun r) {
            double rate = r.getTotalScenarios() == 0 ? 0.0 : (double) r.getPassedCount() / r.getTotalScenarios();
            return new RunSummary(r.getId(), r.getSuiteId(), r.getAgentVersionId(), r.getStatus(),
                    r.getTotalScenarios(), r.getPassedCount(), r.getFailedCount(), rate,
                    r.getAvgLatencyMs(), r.getTotalTokens(), r.getConcurrency(), r.getTokenBudget(),
                    r.getError(), r.getStartedAt(), r.getFinishedAt());
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

    public record TurnDto(int number, String role, String content, Object toolCalls, long latencyMs) {
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
            String stopReason,
            int turnCount,
            List<TurnDto> turns,
            List<EvaluationDto> evaluations) {
    }

    /** judgeTokens is an estimate (characters / 4) of tokens spent on judge calls in this run. */
    public record RunDetail(RunSummary run, long judgeTokens, List<ScenarioResultDto> results) {
    }
}
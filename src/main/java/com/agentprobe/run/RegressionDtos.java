package com.agentprobe.run;

import com.agentprobe.regression.RunComparator;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class RegressionDtos {

    private RegressionDtos() {
    }

    public record BaselineDto(UUID id, UUID suiteId, UUID agentId, UUID runId, Instant createdAt) {
    }

    /** Fractions: 0.10 = 10 points. latencyIncrease and tokenIncrease: 0.5 = +50 percent. */
    public record ThresholdDto(
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double maxPassRateDrop,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double scenarioPassRateDrop,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double scoreDrop,
            @NotNull @DecimalMin("0.0") @DecimalMax("10.0") Double latencyIncrease,
            @NotNull @DecimalMin("0.0") @DecimalMax("10.0") Double tokenIncrease) {
    }

    public record ComparisonResponse(
            UUID baseRunId,
            UUID candidateRunId,
            String baseVersion,
            String candidateVersion,
            RunComparator.Comparison comparison) {
    }
}
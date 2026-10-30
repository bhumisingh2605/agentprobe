package com.agentprobe.run;

import com.agentprobe.calibration.Agreement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LabelingDtos {

    private LabelingDtos() {
    }

    public record SubmitRequest(
            @NotNull UUID scenarioResultId,
            @NotNull Boolean passed,
            @NotNull @Min(1) @Max(5) Integer score,
            @Size(max = 1000) String note) {
    }

    public record TurnView(int number, String role, String content, Object toolCalls) {
    }

    /** Blind view: no evaluator results and no judge output, so the human is not influenced. */
    public record LabelTask(UUID scenarioResultId, String scenarioId, int trial, List<TurnView> turns,
                            Object toolCalls) {
    }

    public record NextResponse(LabelTask task, int total, int labeled) {
    }

    public record LabelDto(UUID id, UUID scenarioResultId, UUID runId, String scenarioId, int trial,
                           boolean passed, int score, String note, Instant createdAt,
                           boolean judged, Boolean judgePassed, Integer judgeScore) {
    }

    public record CalibrationResponse(Agreement.Report report, List<String> judges,
                                      int labeledTotal, int labeledWithJudge) {
    }
}
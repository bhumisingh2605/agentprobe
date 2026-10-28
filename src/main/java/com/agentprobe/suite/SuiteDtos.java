package com.agentprobe.suite;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SuiteDtos {

    private SuiteDtos() {
    }

    public record SuiteRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description) {
    }

    public record SuiteResponse(UUID id, String name, String description, long scenarioCount, Instant createdAt) {
    }

    /** type: single_turn (default) or multi_turn (multi_turn runs from Week 4). */
    public record ScenarioRequest(
            @NotBlank @Size(max = 120) @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                    message = "Use letters, digits, dot, dash or underscore only") String externalId,
            @Size(max = 20) String type,
            @Size(max = 8000) String input,
            @Size(max = 2000) String persona,
            @Size(max = 2000) String goal,
            @Min(1) @Max(30) Integer maxTurns,
            @Size(max = 30) List<String> tags,
            @Size(max = 20) List<Map<String, Object>> evaluators) {
    }

    public record ScenarioResponse(
            UUID id,
            String externalId,
            String type,
            String input,
            String persona,
            String goal,
            Integer maxTurns,
            List<Object> tags,
            List<Object> evaluators,
            Instant createdAt) {
    }

    public record ImportResult(UUID suiteId, String suiteName, int created, int updated) {
    }
}
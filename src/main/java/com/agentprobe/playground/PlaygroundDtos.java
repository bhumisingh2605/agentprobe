package com.agentprobe.playground;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class PlaygroundDtos {

    private PlaygroundDtos() {
    }

    public record SendRequest(
            @NotBlank @Size(max = 8000) String message,
            @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$", message = "sessionId: letters, digits, _ or - (max 64)")
            String sessionId) {
    }

    /** rawBody is only filled for the live call, to help debug wrong response mappings. */
    public record ExchangeResponse(
            UUID id,
            String sessionId,
            String userMessage,
            String agentAnswer,
            Object toolCalls,
            Integer tokens,
            long latencyMs,
            int httpStatus,
            Instant createdAt,
            String rawBody) {
    }
}
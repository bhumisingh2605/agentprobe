package com.agentprobe.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AgentDtos {

    private AgentDtos() {
    }

    public record AgentRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description) {
    }

    public record AgentResponse(UUID id, UUID projectId, String name, String description, Instant createdAt) {
        public static AgentResponse from(Agent a) {
            return new AgentResponse(a.getId(), a.getProjectId(), a.getName(), a.getDescription(), a.getCreatedAt());
        }
    }

    /**
     * requestTemplate e.g. {"message": "{{message}}", "sessionId": "{{sessionId}}"}
     * responseMapping e.g. {"answerPath": "$.answer", "toolCallsPath": "$.toolCalls", "tokensPath": "$.usage.totalTokens"}
     * Both optional; defaults are applied.
     */
    public record VersionRequest(
            @NotBlank @Size(max = 60) @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                    message = "Use letters, digits, dot, dash or underscore only") String versionLabel,
            @NotBlank @Size(max = 2048) String endpointUrl,
            @Size(max = 120) String authHeaderName,
            @Size(max = 2000) String authHeaderValue,
            Map<String, Object> requestTemplate,
            Map<String, Object> responseMapping,
            @Size(max = 120) String model,
            @Size(max = 128) String promptHash,
            Map<String, Object> labels) {
    }

    public record VersionResponse(
            UUID id,
            UUID agentId,
            String versionLabel,
            String endpointUrl,
            String authHeaderName,
            boolean hasAuthSecret,
            Map<String, Object> requestTemplate,
            Map<String, Object> responseMapping,
            String model,
            String promptHash,
            Map<String, Object> labels,
            Instant createdAt) {
    }
}
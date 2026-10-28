package com.agentprobe.playground;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "playground_exchange")
public class PlaygroundExchange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agent_version_id", nullable = false, updatable = false)
    private UUID agentVersionId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "user_message", nullable = false, columnDefinition = "text")
    private String userMessage;

    @Column(name = "agent_answer", columnDefinition = "text")
    private String agentAnswer;

    @Column(name = "tool_calls", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String toolCalls = "[]";

    private Integer tokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PlaygroundExchange() {
    }

    public PlaygroundExchange(UUID agentVersionId, String sessionId, String userMessage, String agentAnswer,
                              String toolCallsJson, Integer tokens, long latencyMs, int httpStatus) {
        this.agentVersionId = agentVersionId;
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.agentAnswer = agentAnswer;
        this.toolCalls = toolCallsJson;
        this.tokens = tokens;
        this.latencyMs = latencyMs;
        this.httpStatus = httpStatus;
    }

    public UUID getId() { return id; }
    public UUID getAgentVersionId() { return agentVersionId; }
    public String getSessionId() { return sessionId; }
    public String getUserMessage() { return userMessage; }
    public String getAgentAnswer() { return agentAnswer; }
    public String getToolCalls() { return toolCalls; }
    public Integer getTokens() { return tokens; }
    public long getLatencyMs() { return latencyMs; }
    public int getHttpStatus() { return httpStatus; }
    public Instant getCreatedAt() { return createdAt; }
}
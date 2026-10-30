
package com.agentprobe.run;

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
@Table(name = "scenario_result")
public class ScenarioResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "scenario_id", nullable = false, updatable = false)
    private UUID scenarioId;

    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean passed;

    @Column(nullable = false)
    private double score;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    private Integer tokens;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "user_message", columnDefinition = "text")
    private String userMessage;

    @Column(name = "agent_answer", columnDefinition = "text")
    private String agentAnswer;

    @Column(name = "tool_calls", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String toolCalls = "[]";

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "stop_reason", length = 40)
    private String stopReason;

    @Column(name = "turn_count", nullable = false)
    private int turnCount = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ScenarioResult() {
    }

    public ScenarioResult(UUID runId, UUID scenarioId,
                          String externalId, int position) {
        this.runId = runId;
        this.scenarioId = scenarioId;
        this.externalId = externalId;
        this.position = position;
    }

    public void fail(String error) {
        this.passed = false;
        this.score = 0.0;
        this.error = error;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public UUID getScenarioId() {
        return scenarioId;
    }

    public String getExternalId() {
        return externalId;
    }

    public int getPosition() {
        return position;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Integer getTokens() {
        return tokens;
    }

    public void setTokens(Integer tokens) {
        this.tokens = tokens;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getAgentAnswer() {
        return agentAnswer;
    }

    public void setAgentAnswer(String agentAnswer) {
        this.agentAnswer = agentAnswer;
    }

    public String getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(String toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getError() {
        return error;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public void setTurnCount(int turnCount) {
        this.turnCount = turnCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

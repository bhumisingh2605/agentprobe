package com.agentprobe.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** status: QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, BUDGET_EXCEEDED */
@Entity
@Table(name = "test_run")
public class TestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "suite_id", nullable = false, updatable = false)
    private UUID suiteId;

    @Column(name = "agent_version_id", nullable = false, updatable = false)
    private UUID agentVersionId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "total_scenarios", nullable = false)
    private int totalScenarios;

    @Column(name = "passed_count", nullable = false)
    private int passedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "avg_latency_ms", nullable = false)
    private long avgLatencyMs;

    @Column(name = "total_tokens", nullable = false)
    private long totalTokens;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "token_budget")
    private Integer tokenBudget;

    @Column(nullable = false)
    private int concurrency;

    protected TestRun() {
    }

    public TestRun(UUID projectId, UUID suiteId, UUID agentVersionId, int totalScenarios,
                   int concurrency, Integer tokenBudget) {
        this.projectId = projectId;
        this.suiteId = suiteId;
        this.agentVersionId = agentVersionId;
        this.totalScenarios = totalScenarios;
        this.concurrency = concurrency;
        this.tokenBudget = tokenBudget;
        this.status = "QUEUED";
    }

    public void markRunning() {
        this.status = "RUNNING";
        this.startedAt = Instant.now();
    }

    /** Live progress, called after every finished scenario. */
    public void progress(int passed, int failed, long avgLatencyMs, long totalTokens) {
        this.passedCount = passed;
        this.failedCount = failed;
        this.avgLatencyMs = avgLatencyMs;
        this.totalTokens = totalTokens;
    }

    /** Final state: COMPLETED, FAILED, CANCELLED or BUDGET_EXCEEDED. */
    public void end(String status, String error, int passed, int failed, long avgLatencyMs, long totalTokens) {
        progress(passed, failed, avgLatencyMs, totalTokens);
        this.status = status;
        this.error = error;
        this.finishedAt = Instant.now();
    }

    public boolean isActive() {
        return "QUEUED".equals(status) || "RUNNING".equals(status);
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getSuiteId() { return suiteId; }
    public UUID getAgentVersionId() { return agentVersionId; }
    public String getStatus() { return status; }
    public int getTotalScenarios() { return totalScenarios; }
    public int getPassedCount() { return passedCount; }
    public int getFailedCount() { return failedCount; }
    public long getAvgLatencyMs() { return avgLatencyMs; }
    public long getTotalTokens() { return totalTokens; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getError() { return error; }
    public Integer getTokenBudget() { return tokenBudget; }
    public int getConcurrency() { return concurrency; }
}
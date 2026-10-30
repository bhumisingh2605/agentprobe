package com.agentprobe.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** The run that counts as "known good" for one suite and one agent. */
@Entity
@Table(name = "baseline")
public class Baseline {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "suite_id", nullable = false, updatable = false)
    private UUID suiteId;

    @Column(name = "agent_id", nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Baseline() {
    }

    public Baseline(UUID suiteId, UUID agentId, UUID runId) {
        this.suiteId = suiteId;
        this.agentId = agentId;
        this.runId = runId;
    }

    public void changeRun(UUID runId) {
        this.runId = runId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSuiteId() { return suiteId; }
    public UUID getAgentId() { return agentId; }
    public UUID getRunId() { return runId; }
    public Instant getCreatedAt() { return createdAt; }
}
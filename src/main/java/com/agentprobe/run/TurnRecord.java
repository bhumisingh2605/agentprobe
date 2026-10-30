package com.agentprobe.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.util.UUID;

@Entity
@Table(name = "turn")
public class TurnRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scenario_result_id", nullable = false, updatable = false)
    private UUID scenarioResultId;

    @Column(name = "turn_number", nullable = false)
    private int turnNumber;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "tool_calls", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String toolCalls = "[]";

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    protected TurnRecord() {
    }

    public TurnRecord(UUID scenarioResultId, int turnNumber, String role, String content,
                      String toolCalls, long latencyMs) {
        this.scenarioResultId = scenarioResultId;
        this.turnNumber = turnNumber;
        this.role = role;
        this.content = content == null ? "" : content;
        this.toolCalls = toolCalls == null ? "[]" : toolCalls;
        this.latencyMs = latencyMs;
    }

    public UUID getId() { return id; }
    public UUID getScenarioResultId() { return scenarioResultId; }
    public int getTurnNumber() { return turnNumber; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getToolCalls() { return toolCalls; }
    public long getLatencyMs() { return latencyMs; }
}
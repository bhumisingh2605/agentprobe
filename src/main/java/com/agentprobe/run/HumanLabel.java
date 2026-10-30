package com.agentprobe.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "human_label")
public class HumanLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scenario_result_id", nullable = false, updatable = false)
    private UUID scenarioResultId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean passed;

    @Column(nullable = false)
    private int score;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected HumanLabel() {
    }

    public HumanLabel(UUID scenarioResultId, UUID userId, boolean passed, int score, String note) {
        this.scenarioResultId = scenarioResultId;
        this.userId = userId;
        this.passed = passed;
        this.score = score;
        this.note = note;
    }

    public void update(boolean passed, int score, String note) {
        this.passed = passed;
        this.score = score;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getScenarioResultId() { return scenarioResultId; }
    public UUID getUserId() { return userId; }
    public boolean isPassed() { return passed; }
    public int getScore() { return score; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
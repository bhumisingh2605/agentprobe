package com.agentprobe.suite;

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
@Table(name = "scenario")
public class Scenario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "suite_id", nullable = false, updatable = false)
    private UUID suiteId;

    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(columnDefinition = "text")
    private String input;

    @Column(columnDefinition = "text")
    private String persona;

    @Column(columnDefinition = "text")
    private String goal;

    @Column(name = "max_turns")
    private Integer maxTurns;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String tags = "[]";

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String evaluators = "[]";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Scenario() {
    }

    public Scenario(UUID suiteId, String externalId, String type) {
        this.suiteId = suiteId;
        this.externalId = externalId;
        this.type = type;
    }

    public UUID getId() { return id; }
    public UUID getSuiteId() { return suiteId; }
    public String getExternalId() { return externalId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getPersona() { return persona; }
    public void setPersona(String persona) { this.persona = persona; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public Integer getMaxTurns() { return maxTurns; }
    public void setMaxTurns(Integer maxTurns) { this.maxTurns = maxTurns; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getEvaluators() { return evaluators; }
    public void setEvaluators(String evaluators) { this.evaluators = evaluators; }
    public Instant getCreatedAt() { return createdAt; }
}
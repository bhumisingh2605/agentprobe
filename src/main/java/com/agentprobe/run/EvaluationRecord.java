package com.agentprobe.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "evaluation_result")
public class EvaluationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scenario_result_id", nullable = false, updatable = false)
    private UUID scenarioResultId;

    @Column(name = "evaluator_type", nullable = false, length = 60)
    private String evaluatorType;

    @Column(nullable = false)
    private boolean passed;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private double weight;

    @Column(columnDefinition = "text")
    private String reasoning;

    protected EvaluationRecord() {
    }

    public EvaluationRecord(UUID scenarioResultId, String evaluatorType, boolean passed, double score,
                            double weight, String reasoning) {
        this.scenarioResultId = scenarioResultId;
        this.evaluatorType = evaluatorType;
        this.passed = passed;
        this.score = score;
        this.weight = weight;
        this.reasoning = reasoning;
    }

    public UUID getId() { return id; }
    public UUID getScenarioResultId() { return scenarioResultId; }
    public String getEvaluatorType() { return evaluatorType; }
    public boolean isPassed() { return passed; }
    public double getScore() { return score; }
    public double getWeight() { return weight; }
    public String getReasoning() { return reasoning; }
}
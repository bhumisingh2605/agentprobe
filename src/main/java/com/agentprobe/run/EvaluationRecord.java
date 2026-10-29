package com.agentprobe.run;

import com.agentprobe.evaluation.EvaluationResult;
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

    @Column(name = "judge_model", length = 120)
    private String judgeModel;

    @Column(name = "judge_prompt_version", length = 40)
    private String judgePromptVersion;

    @Column(name = "judge_tokens")
    private Integer judgeTokens;

    @Column(name = "judge_latency_ms")
    private Long judgeLatencyMs;

    protected EvaluationRecord() {
    }

    public EvaluationRecord(UUID scenarioResultId, EvaluationResult r, double weight) {
        this.scenarioResultId = scenarioResultId;
        this.evaluatorType = r.type();
        this.passed = r.passed();
        this.score = r.score();
        this.weight = weight;
        this.reasoning = r.reasoning();
        this.judgeModel = r.judgeModel();
        this.judgePromptVersion = r.judgePromptVersion();
        this.judgeTokens = r.judgeTokens();
        this.judgeLatencyMs = r.judgeLatencyMs();
    }

    public UUID getId() { return id; }
    public UUID getScenarioResultId() { return scenarioResultId; }
    public String getEvaluatorType() { return evaluatorType; }
    public boolean isPassed() { return passed; }
    public double getScore() { return score; }
    public double getWeight() { return weight; }
    public String getReasoning() { return reasoning; }
    public String getJudgeModel() { return judgeModel; }
    public String getJudgePromptVersion() { return judgePromptVersion; }
    public Integer getJudgeTokens() { return judgeTokens; }
    public Long getJudgeLatencyMs() { return judgeLatencyMs; }
}
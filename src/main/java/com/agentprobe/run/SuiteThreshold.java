package com.agentprobe.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Optional per-suite regression thresholds. Without a row, defaults apply. */
@Entity
@Table(name = "suite_threshold")
public class SuiteThreshold {

    @Id
    @Column(name = "suite_id")
    private UUID suiteId;

    @Column(name = "max_pass_rate_drop", nullable = false)
    private double maxPassRateDrop;

    @Column(name = "scenario_pass_rate_drop", nullable = false)
    private double scenarioPassRateDrop;

    @Column(name = "score_drop", nullable = false)
    private double scoreDrop;

    @Column(name = "latency_increase", nullable = false)
    private double latencyIncrease;

    @Column(name = "token_increase", nullable = false)
    private double tokenIncrease;

    protected SuiteThreshold() {
    }

    public SuiteThreshold(UUID suiteId, double maxPassRateDrop, double scenarioPassRateDrop, double scoreDrop,
                          double latencyIncrease, double tokenIncrease) {
        this.suiteId = suiteId;
        set(maxPassRateDrop, scenarioPassRateDrop, scoreDrop, latencyIncrease, tokenIncrease);
    }

    public void set(double maxPassRateDrop, double scenarioPassRateDrop, double scoreDrop,
                    double latencyIncrease, double tokenIncrease) {
        this.maxPassRateDrop = maxPassRateDrop;
        this.scenarioPassRateDrop = scenarioPassRateDrop;
        this.scoreDrop = scoreDrop;
        this.latencyIncrease = latencyIncrease;
        this.tokenIncrease = tokenIncrease;
    }

    public UUID getSuiteId() { return suiteId; }
    public double getMaxPassRateDrop() { return maxPassRateDrop; }
    public double getScenarioPassRateDrop() { return scenarioPassRateDrop; }
    public double getScoreDrop() { return scoreDrop; }
    public double getLatencyIncrease() { return latencyIncrease; }
    public double getTokenIncrease() { return tokenIncrease; }
}
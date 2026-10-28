package com.agentprobe.evaluation;

/** score is between 0.0 and 1.0. */
public record EvaluationResult(String type, boolean passed, double score, String reasoning) {

    public static EvaluationResult pass(String type, String reasoning) {
        return new EvaluationResult(type, true, 1.0, reasoning);
    }

    public static EvaluationResult fail(String type, String reasoning) {
        return new EvaluationResult(type, false, 0.0, reasoning);
    }
}
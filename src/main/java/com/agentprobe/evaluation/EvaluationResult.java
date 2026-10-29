package com.agentprobe.evaluation;

/**
 * score is between 0.0 and 1.0. The judge* fields are only filled by LLM-based evaluators,
 * so scores stay comparable later (same judge model + prompt version).
 */
public record EvaluationResult(
        String type,
        boolean passed,
        double score,
        String reasoning,
        String judgeModel,
        String judgePromptVersion,
        Integer judgeTokens,
        Long judgeLatencyMs) {

    public EvaluationResult(String type, boolean passed, double score, String reasoning) {
        this(type, passed, score, reasoning, null, null, null, null);
    }

    public static EvaluationResult pass(String type, String reasoning) {
        return new EvaluationResult(type, true, 1.0, reasoning);
    }

    public static EvaluationResult fail(String type, String reasoning) {
        return new EvaluationResult(type, false, 0.0, reasoning);
    }
}
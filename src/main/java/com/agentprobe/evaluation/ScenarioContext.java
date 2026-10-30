package com.agentprobe.evaluation;

import java.util.List;

/**
 * Everything an evaluator can look at for one scenario execution.
 * Single-turn: input is the question, answer is the reply.
 * Multi-turn: input is the first customer message, answer is all agent replies joined,
 * toolCalls are from every turn, transcript is the full conversation, latencyMs is the slowest single turn.
 */
public record ScenarioContext(
        String input,
        String answer,
        List<Object> toolCalls,
        Integer tokens,
        long latencyMs,
        int httpStatus,
        String transcript,
        int turns,
        String stopReason) {

    public ScenarioContext(String input, String answer, List<Object> toolCalls, Integer tokens,
                           long latencyMs, int httpStatus) {
        this(input, answer, toolCalls, tokens, latencyMs, httpStatus, "", 1, "single_turn");
    }
}
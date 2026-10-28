
package com.agentprobe.evaluation;

import java.util.List;

public record ScenarioContext(
        String input,
        String answer,
        List<?> toolCalls,
        Integer tokens,
        long latencyMs,
        int httpStatus
) {
    public ScenarioContext {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}

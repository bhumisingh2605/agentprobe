package com.agentprobe.adapter;

import java.util.List;

/** answer is null when the call failed or the mapping found nothing; check httpStatus and rawBody. */
public record AgentReply(
        String answer,
        List<Object> toolCalls,
        Integer tokens,
        long latencyMs,
        int httpStatus,
        String rawBody) {
}
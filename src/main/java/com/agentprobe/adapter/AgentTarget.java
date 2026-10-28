package com.agentprobe.adapter;

/** Everything an adapter needs to call one agent version. authHeaderValue is already decrypted. */
public record AgentTarget(
        String endpointUrl,
        String authHeaderName,
        String authHeaderValue,
        String requestTemplateJson,
        String responseMappingJson) {
}
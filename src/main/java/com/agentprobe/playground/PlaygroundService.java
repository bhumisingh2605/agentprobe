package com.agentprobe.playground;

import com.agentprobe.adapter.AgentAdapter;
import com.agentprobe.adapter.AgentReply;
import com.agentprobe.adapter.AgentTarget;
import com.agentprobe.agent.AgentService;
import net.minidev.json.JSONValue;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Not @Transactional on purpose: the agent call can take seconds and must not hold a DB transaction.
 */
@Service
public class PlaygroundService {

    private final AgentService agentService;
    private final AgentAdapter adapter;
    private final PlaygroundExchangeRepository exchanges;

    public PlaygroundService(AgentService agentService, AgentAdapter adapter,
                             PlaygroundExchangeRepository exchanges) {
        this.agentService = agentService;
        this.adapter = adapter;
        this.exchanges = exchanges;
    }

    public PlaygroundDtos.ExchangeResponse send(UUID projectId, UUID agentId, UUID versionId, UUID userId,
                                                String message, String sessionId) {
        AgentTarget target = agentService.resolveTarget(projectId, agentId, versionId, userId);
        String session = sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId;

        AgentReply reply = adapter.send(target, message, session);

        PlaygroundExchange saved = exchanges.save(new PlaygroundExchange(
                versionId, session, message, reply.answer(),
                JSONValue.toJSONString(reply.toolCalls()), reply.tokens(), reply.latencyMs(), reply.httpStatus()));
        return toResponse(saved, reply.rawBody());
    }

    public List<PlaygroundDtos.ExchangeResponse> history(UUID projectId, UUID agentId, UUID versionId, UUID userId) {
        agentService.resolveTarget(projectId, agentId, versionId, userId); // ownership check
        return exchanges.findByAgentVersionIdOrderByCreatedAtDesc(versionId, PageRequest.of(0, 50)).stream()
                .map(e -> toResponse(e, null))
                .toList();
    }

    private static PlaygroundDtos.ExchangeResponse toResponse(PlaygroundExchange e, String raw) {
        Object toolCalls = JSONValue.parse(e.getToolCalls());
        return new PlaygroundDtos.ExchangeResponse(e.getId(), e.getSessionId(), e.getUserMessage(),
                e.getAgentAnswer(), toolCalls == null ? List.of() : toolCalls, e.getTokens(),
                e.getLatencyMs(), e.getHttpStatus(), e.getCreatedAt(), raw);
    }
}
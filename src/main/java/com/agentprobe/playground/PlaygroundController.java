package com.agentprobe.playground;

import com.agentprobe.playground.PlaygroundDtos.ExchangeResponse;
import com.agentprobe.playground.PlaygroundDtos.SendRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/agents/{agentId}/versions/{versionId}")
public class PlaygroundController {

    private final PlaygroundService service;

    public PlaygroundController(PlaygroundService service) {
        this.service = service;
    }

    @PostMapping("/test")
    public ExchangeResponse test(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                 @PathVariable UUID agentId, @PathVariable UUID versionId,
                                 @Valid @RequestBody SendRequest req) {
        return service.send(projectId, agentId, versionId, UUID.fromString(jwt.getSubject()),
                req.message(), req.sessionId());
    }

    @GetMapping("/exchanges")
    public List<ExchangeResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                          @PathVariable UUID agentId, @PathVariable UUID versionId) {
        return service.history(projectId, agentId, versionId, UUID.fromString(jwt.getSubject()));
    }
}
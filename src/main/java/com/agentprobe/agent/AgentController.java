package com.agentprobe.agent;

import com.agentprobe.agent.AgentDtos.AgentRequest;
import com.agentprobe.agent.AgentDtos.AgentResponse;
import com.agentprobe.agent.AgentDtos.VersionRequest;
import com.agentprobe.agent.AgentDtos.VersionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/agents")
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @GetMapping
    public List<AgentResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.listAgents(projectId, userId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                @Valid @RequestBody AgentRequest req) {
        return service.createAgent(projectId, userId(jwt), req);
    }

    @GetMapping("/{agentId}")
    public AgentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                             @PathVariable UUID agentId) {
        return service.getAgent(projectId, agentId, userId(jwt));
    }

    @PutMapping("/{agentId}")
    public AgentResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                @PathVariable UUID agentId, @Valid @RequestBody AgentRequest req) {
        return service.updateAgent(projectId, agentId, userId(jwt), req);
    }

    @DeleteMapping("/{agentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                       @PathVariable UUID agentId) {
        service.deleteAgent(projectId, agentId, userId(jwt));
    }

    @GetMapping("/{agentId}/versions")
    public List<VersionResponse> listVersions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                              @PathVariable UUID agentId) {
        return service.listVersions(projectId, agentId, userId(jwt));
    }

    @PostMapping("/{agentId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public VersionResponse createVersion(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                         @PathVariable UUID agentId, @Valid @RequestBody VersionRequest req) {
        return service.createVersion(projectId, agentId, userId(jwt), req);
    }

    @GetMapping("/{agentId}/versions/{versionId}")
    public VersionResponse getVersion(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                      @PathVariable UUID agentId, @PathVariable UUID versionId) {
        return service.getVersion(projectId, agentId, versionId, userId(jwt));
    }

    @DeleteMapping("/{agentId}/versions/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVersion(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                              @PathVariable UUID agentId, @PathVariable UUID versionId) {
        service.deleteVersion(projectId, agentId, versionId, userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
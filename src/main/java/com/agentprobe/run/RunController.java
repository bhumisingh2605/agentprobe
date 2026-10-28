package com.agentprobe.run;

import com.agentprobe.run.RunDtos.RunDetail;
import com.agentprobe.run.RunDtos.RunSummary;
import com.agentprobe.run.RunDtos.StartRunRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class RunController {

    private final RunService service;

    public RunController(RunService service) {
        this.service = service;
    }

    /** Runs the whole suite against one agent version and waits for the result (can take a while). */
    @PostMapping("/suites/{suiteId}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public RunDetail start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                           @PathVariable UUID suiteId, @Valid @RequestBody StartRunRequest req) {
        return service.start(projectId, suiteId, req.agentId(), req.versionId(), userId(jwt));
    }

    @GetMapping("/suites/{suiteId}/runs")
    public List<RunSummary> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                 @PathVariable UUID suiteId) {
        return service.list(projectId, suiteId, userId(jwt));
    }

    @GetMapping("/runs/{runId}")
    public RunDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                         @PathVariable UUID runId) {
        return service.get(projectId, runId, userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
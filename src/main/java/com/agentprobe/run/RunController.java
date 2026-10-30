package com.agentprobe.run;

import com.agentprobe.run.RunDtos.RunDetail;
import com.agentprobe.run.RunDtos.RunSummary;
import com.agentprobe.run.RunDtos.StartRunRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class RunController {

    private final RunService service;

    public RunController(RunService service) {
        this.service = service;
    }

    /** Queues the run and returns immediately. Poll GET /runs/{runId} or stream /runs/{runId}/events. */
    @PostMapping("/suites/{suiteId}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunSummary start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                            @PathVariable UUID suiteId, @Valid @RequestBody StartRunRequest req) {
        return service.start(projectId, suiteId, req, userId(jwt));
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

    @PostMapping("/runs/{runId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunSummary cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                             @PathVariable UUID runId) {
        return service.cancel(projectId, runId, userId(jwt));
    }

    /** Server-Sent Events: snapshot, scenario (after each scenario), progress, done. */
    @GetMapping(value = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                             @PathVariable UUID runId) {
        return service.subscribe(projectId, runId, userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
package com.agentprobe.suite;

import com.agentprobe.suite.SuiteDtos.ImportResult;
import com.agentprobe.suite.SuiteDtos.ScenarioRequest;
import com.agentprobe.suite.SuiteDtos.ScenarioResponse;
import com.agentprobe.suite.SuiteDtos.SuiteRequest;
import com.agentprobe.suite.SuiteDtos.SuiteResponse;
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
@RequestMapping("/api/projects/{projectId}/suites")
public class SuiteController {

    private final SuiteService service;

    public SuiteController(SuiteService service) {
        this.service = service;
    }

    @GetMapping
    public List<SuiteResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.listSuites(projectId, userId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuiteResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                @Valid @RequestBody SuiteRequest req) {
        return service.createSuite(projectId, userId(jwt), req);
    }

    @GetMapping("/{suiteId}")
    public SuiteResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                             @PathVariable UUID suiteId) {
        return service.getSuite(projectId, suiteId, userId(jwt));
    }

    @DeleteMapping("/{suiteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                       @PathVariable UUID suiteId) {
        service.deleteSuite(projectId, suiteId, userId(jwt));
    }

    // ----- scenarios -----

    @GetMapping("/{suiteId}/scenarios")
    public List<ScenarioResponse> listScenarios(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                                @PathVariable UUID suiteId) {
        return service.listScenarios(projectId, suiteId, userId(jwt));
    }

    @PostMapping("/{suiteId}/scenarios")
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioResponse createScenario(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                           @PathVariable UUID suiteId, @Valid @RequestBody ScenarioRequest req) {
        return service.createScenario(projectId, suiteId, userId(jwt), req);
    }

    @PutMapping("/{suiteId}/scenarios/{scenarioId}")
    public ScenarioResponse updateScenario(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                           @PathVariable UUID suiteId, @PathVariable UUID scenarioId,
                                           @Valid @RequestBody ScenarioRequest req) {
        return service.updateScenario(projectId, suiteId, scenarioId, userId(jwt), req);
    }

    @DeleteMapping("/{suiteId}/scenarios/{scenarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScenario(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                               @PathVariable UUID suiteId, @PathVariable UUID scenarioId) {
        service.deleteScenario(projectId, suiteId, scenarioId, userId(jwt));
    }

    // ----- YAML import / export -----

    /** Send the YAML text as the request body (Content-Type: text/plain). Creates or updates the suite by name. */
    @PostMapping(value = "/import", consumes = {"text/plain", "application/yaml", "application/x-yaml"})
    public ImportResult importYaml(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                   @RequestBody String body) {
        return service.importYaml(projectId, userId(jwt), body);
    }

    @GetMapping(value = "/{suiteId}/export", produces = "text/plain")
    public String exportYaml(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                             @PathVariable UUID suiteId) {
        return service.exportYaml(projectId, suiteId, userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
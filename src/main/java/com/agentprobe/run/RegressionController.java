package com.agentprobe.run;

import com.agentprobe.regression.ComparisonReport;
import com.agentprobe.run.RegressionDtos.BaselineDto;
import com.agentprobe.run.RegressionDtos.ComparisonResponse;
import com.agentprobe.run.RegressionDtos.ThresholdDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class RegressionController {

    private final RegressionService service;

    public RegressionController(RegressionService service) {
        this.service = service;
    }

    // ----- baselines -----

    /** Marks a COMPLETED run as the baseline for its suite and agent (replaces the previous one). */
    @PostMapping("/runs/{runId}/baseline")
    public BaselineDto markBaseline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                    @PathVariable UUID runId) {
        return service.markBaseline(projectId, runId, userId(jwt));
    }

    @GetMapping("/suites/{suiteId}/baselines")
    public List<BaselineDto> baselines(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                       @PathVariable UUID suiteId) {
        return service.listBaselines(projectId, suiteId, userId(jwt));
    }

    // ----- thresholds -----

    @GetMapping("/suites/{suiteId}/thresholds")
    public ThresholdDto getThresholds(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                      @PathVariable UUID suiteId) {
        return service.getThresholds(projectId, suiteId, userId(jwt));
    }

    @PutMapping("/suites/{suiteId}/thresholds")
    public ThresholdDto putThresholds(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                      @PathVariable UUID suiteId, @Valid @RequestBody ThresholdDto dto) {
        return service.putThresholds(projectId, suiteId, userId(jwt), dto);
    }

    // ----- comparison -----

    @GetMapping("/runs/compare")
    public ComparisonResponse compare(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                      @RequestParam UUID baseRunId, @RequestParam UUID candidateRunId) {
        return service.compare(projectId, baseRunId, candidateRunId, userId(jwt));
    }

    @GetMapping(value = "/runs/compare/report", produces = "text/markdown")
    public String compareReport(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                @RequestParam UUID baseRunId, @RequestParam UUID candidateRunId) {
        return markdown(service.compare(projectId, baseRunId, candidateRunId, userId(jwt)));
    }

    @GetMapping("/runs/{runId}/compare-to-baseline")
    public ComparisonResponse compareToBaseline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                                @PathVariable UUID runId) {
        return service.compareToBaseline(projectId, runId, userId(jwt));
    }

    @GetMapping(value = "/runs/{runId}/compare-to-baseline/report", produces = "text/markdown")
    public String compareToBaselineReport(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                          @PathVariable UUID runId) {
        return markdown(service.compareToBaseline(projectId, runId, userId(jwt)));
    }

    private static String markdown(ComparisonResponse r) {
        return ComparisonReport.toMarkdown(r.baseVersion(), r.candidateVersion(),
                r.baseRunId().toString(), r.candidateRunId().toString(), r.comparison());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
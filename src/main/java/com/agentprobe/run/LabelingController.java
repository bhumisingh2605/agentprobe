package com.agentprobe.run;

import com.agentprobe.run.LabelingDtos.CalibrationResponse;
import com.agentprobe.run.LabelingDtos.LabelDto;
import com.agentprobe.run.LabelingDtos.NextResponse;
import com.agentprobe.run.LabelingDtos.SubmitRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/labeling")
public class LabelingController {

    private final LabelingService service;

    public LabelingController(LabelingService service) {
        this.service = service;
    }

    /** Next unlabeled result of a run, shown WITHOUT any judge or evaluator output (blind labeling). */
    @GetMapping("/next")
    public NextResponse next(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                             @RequestParam UUID runId, @RequestParam(defaultValue = "true") boolean judgedOnly) {
        return service.next(projectId, runId, userId(jwt), judgedOnly);
    }

    @PostMapping("/labels")
    @ResponseStatus(HttpStatus.CREATED)
    public LabelDto submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                           @Valid @RequestBody SubmitRequest req) {
        return service.submit(projectId, userId(jwt), req);
    }

    @GetMapping("/labels")
    public List<LabelDto> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.list(projectId, userId(jwt));
    }

    @DeleteMapping("/labels/{labelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID labelId) {
        service.delete(projectId, userId(jwt), labelId);
    }

    @GetMapping("/calibration")
    public CalibrationResponse calibration(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.calibration(projectId, userId(jwt));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public String export(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.exportCsv(projectId, userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
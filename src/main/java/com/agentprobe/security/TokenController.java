package com.agentprobe.security;

import com.agentprobe.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/tokens")
public class TokenController {

    public record CreateRequest(@NotBlank @Size(max = 80) String name) {
    }

    private final ApiTokenService service;

    public TokenController(ApiTokenService service) {
        this.service = service;
    }

    /** The plaintext token is returned only here, once. Store it as a CI secret. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiTokenService.Created create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest req) {
        requireInteractive(jwt);
        return service.create(UUID.fromString(jwt.getSubject()), req.name());
    }

    @GetMapping
    public List<ApiTokenService.Info> list(@AuthenticationPrincipal Jwt jwt) {
        requireInteractive(jwt);
        return service.list(UUID.fromString(jwt.getSubject()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        requireInteractive(jwt);
        service.revoke(UUID.fromString(jwt.getSubject()), id);
    }

    /** A leaked API token must not be able to mint more tokens. */
    private static void requireInteractive(Jwt jwt) {
        if ("api".equals(jwt.getClaimAsString("token_type"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "API tokens cannot manage API tokens");
        }
    }
}
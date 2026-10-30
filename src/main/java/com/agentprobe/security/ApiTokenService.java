package com.agentprobe.security;

import com.agentprobe.api.ApiException;
import com.agentprobe.user.User;
import com.agentprobe.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Long-lived, revocable tokens for CI (the CLI). They authenticate as the owning user but carry
 * token_type=api, which blocks token management and any future user-security endpoints.
 */
@Service
public class ApiTokenService {

    public static final String PREFIX = "apt_";
    private static final int MAX_ACTIVE_TOKENS = 10;

    public record Created(UUID id, String name, String token, String prefix, Instant createdAt) {
    }

    public record Info(UUID id, String name, String prefix, boolean revoked, Instant createdAt, Instant lastUsedAt) {
    }

    private final ApiTokenRepository tokens;
    private final UserRepository users;
    private final SecureRandom random = new SecureRandom();

    public ApiTokenService(ApiTokenRepository tokens, UserRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Transactional
    public Created create(UUID userId, String name) {
        if (tokens.countByUserIdAndRevokedFalse(userId) >= MAX_ACTIVE_TOKENS) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have " + MAX_ACTIVE_TOKENS
                    + " active API tokens. Revoke one first.");
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        ApiToken saved = tokens.save(new ApiToken(userId, name.trim(), token.substring(0, 8), sha256(token)));
        return new Created(saved.getId(), saved.getName(), token, saved.getPrefix(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<Info> list(UUID userId) {
        List<Info> out = new ArrayList<Info>();
        for (ApiToken t : tokens.findAllByUserIdOrderByCreatedAtDesc(userId)) {
            out.add(new Info(t.getId(), t.getName(), t.getPrefix(), t.isRevoked(), t.getCreatedAt(), t.getLastUsedAt()));
        }
        return out;
    }

    @Transactional
    public void revoke(UUID userId, UUID tokenId) {
        ApiToken t = tokens.findByIdAndUserId(tokenId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Token not found"));
        t.revoke();
    }

    /** Returns an authentication for a valid, non-revoked token, otherwise empty. */
    @Transactional
    public Optional<Authentication> authenticate(String token) {
        if (token == null || !token.startsWith(PREFIX) || token.length() > 100) {
            return Optional.empty();
        }
        ApiToken t = tokens.findByTokenHash(sha256(token)).orElse(null);
        if (t == null || t.isRevoked()) {
            return Optional.empty();
        }
        User user = users.findById(t.getUserId()).orElse(null);
        if (user == null) {
            return Optional.empty();
        }
        t.touch();

        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("token_type", "api")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        Authentication auth = new JwtAuthenticationToken(jwt, List.of());
        return Optional.of(auth);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
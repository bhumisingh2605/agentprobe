package com.agentprobe.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Only the SHA-256 hash of the token is stored. The plaintext is shown once at creation. */
@Entity
@Table(name = "api_token")
public class ApiToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 16)
    private String prefix;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected ApiToken() {
    }

    public ApiToken(UUID userId, String name, String prefix, String tokenHash) {
        this.userId = userId;
        this.name = name;
        this.prefix = prefix;
        this.tokenHash = tokenHash;
    }

    public void revoke() { this.revoked = true; }

    /** Returns true when the timestamp was updated (at most once a minute). */
    public boolean touch() {
        Instant now = Instant.now();
        if (lastUsedAt == null || lastUsedAt.isBefore(now.minusSeconds(60))) {
            lastUsedAt = now;
            return true;
        }
        return false;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getPrefix() { return prefix; }
    public String getTokenHash() { return tokenHash; }
    public boolean isRevoked() { return revoked; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
}
package com.agentprobe.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {

    Optional<ApiToken> findByTokenHash(String tokenHash);

    List<ApiToken> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ApiToken> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndRevokedFalse(UUID userId);
}
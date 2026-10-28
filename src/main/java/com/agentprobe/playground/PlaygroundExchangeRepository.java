package com.agentprobe.playground;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlaygroundExchangeRepository extends JpaRepository<PlaygroundExchange, UUID> {

    List<PlaygroundExchange> findByAgentVersionIdOrderByCreatedAtDesc(UUID agentVersionId, Pageable pageable);
}
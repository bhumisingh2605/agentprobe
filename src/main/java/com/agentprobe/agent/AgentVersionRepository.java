package com.agentprobe.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentVersionRepository extends JpaRepository<AgentVersion, UUID> {

    List<AgentVersion> findAllByAgentIdOrderByCreatedAtDesc(UUID agentId);

    Optional<AgentVersion> findByIdAndAgentId(UUID id, UUID agentId);

    boolean existsByAgentIdAndVersionLabel(UUID agentId, String versionLabel);
}
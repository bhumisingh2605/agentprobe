package com.agentprobe.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<Agent, UUID> {

    List<Agent> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<Agent> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByProjectIdAndName(UUID projectId, String name);

    boolean existsByProjectIdAndNameAndIdNot(UUID projectId, String name, UUID id);
}
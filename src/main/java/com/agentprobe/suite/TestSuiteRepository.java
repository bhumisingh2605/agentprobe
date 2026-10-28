package com.agentprobe.suite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestSuiteRepository extends JpaRepository<TestSuite, UUID> {

    List<TestSuite> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<TestSuite> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<TestSuite> findByProjectIdAndName(UUID projectId, String name);

    boolean existsByProjectIdAndName(UUID projectId, String name);
}
package com.agentprobe.suite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {

    List<Scenario> findAllBySuiteIdOrderByExternalIdAsc(UUID suiteId);

    Optional<Scenario> findByIdAndSuiteId(UUID id, UUID suiteId);

    Optional<Scenario> findBySuiteIdAndExternalId(UUID suiteId, String externalId);

    boolean existsBySuiteIdAndExternalId(UUID suiteId, String externalId);

    long countBySuiteId(UUID suiteId);
}
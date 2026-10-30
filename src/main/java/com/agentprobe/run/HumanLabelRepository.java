package com.agentprobe.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HumanLabelRepository extends JpaRepository<HumanLabel, UUID> {

    Optional<HumanLabel> findByScenarioResultIdAndUserId(UUID scenarioResultId, UUID userId);

    Optional<HumanLabel> findByIdAndUserId(UUID id, UUID userId);

    List<HumanLabel> findAllByUserId(UUID userId);

    List<HumanLabel> findAllByUserIdAndScenarioResultIdIn(UUID userId, Collection<UUID> ids);
}
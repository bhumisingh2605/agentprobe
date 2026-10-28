package com.agentprobe.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TestRunRepository extends JpaRepository<TestRun, UUID> {

    Optional<TestRun> findByIdAndProjectId(UUID id, UUID projectId);

    List<TestRun> findAllByProjectIdAndSuiteIdOrderByCreatedAtDesc(UUID projectId, UUID suiteId);
}

interface ScenarioResultRepository extends JpaRepository<ScenarioResult, UUID> {

    List<ScenarioResult> findAllByRunIdOrderByPositionAsc(UUID runId);
}

interface EvaluationRecordRepository extends JpaRepository<EvaluationRecord, UUID> {

    List<EvaluationRecord> findAllByScenarioResultIdIn(Collection<UUID> ids);
}
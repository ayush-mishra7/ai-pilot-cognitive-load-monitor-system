package com.aipclm.system.risk.repository;

import com.aipclm.system.risk.model.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {
    Optional<RiskAssessment> findTopByCognitiveStateTelemetryFrameFlightSessionIdOrderByTimestampDesc(UUID sessionId);

    Optional<RiskAssessment> findByCognitiveStateId(UUID cognitiveStateId);
}

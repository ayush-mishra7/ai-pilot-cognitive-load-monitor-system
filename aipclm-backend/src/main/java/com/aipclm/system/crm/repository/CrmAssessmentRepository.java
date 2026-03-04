package com.aipclm.system.crm.repository;

import com.aipclm.system.crm.model.CrmAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrmAssessmentRepository extends JpaRepository<CrmAssessment, UUID> {

    List<CrmAssessment> findByFlightSessionIdOrderByFrameNumberAsc(UUID sessionId);

    Optional<CrmAssessment> findTopByFlightSessionIdOrderByFrameNumberDesc(UUID sessionId);
}

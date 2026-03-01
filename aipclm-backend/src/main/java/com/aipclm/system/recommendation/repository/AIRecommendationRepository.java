package com.aipclm.system.recommendation.repository;

import com.aipclm.system.recommendation.model.AIRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AIRecommendationRepository extends JpaRepository<AIRecommendation, UUID> {

    // Explicit query to avoid N+1 loading the entire collection off the parent
    // RiskAssessment entity
    List<AIRecommendation> findByRiskAssessmentId(UUID riskAssessmentId);
}

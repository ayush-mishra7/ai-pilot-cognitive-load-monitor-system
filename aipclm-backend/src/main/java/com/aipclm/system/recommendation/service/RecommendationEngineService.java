package com.aipclm.system.recommendation.service;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.model.RiskLevel;
import com.aipclm.system.recommendation.model.AIRecommendation;
import com.aipclm.system.recommendation.model.RecommendationType;
import com.aipclm.system.recommendation.model.Severity;
import com.aipclm.system.recommendation.repository.AIRecommendationRepository;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.risk.repository.RiskAssessmentRepository;
import com.aipclm.system.telemetry.model.PhaseOfFlight;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class RecommendationEngineService {

    private final RiskAssessmentRepository riskAssessmentRepository;
    private final AIRecommendationRepository aiRecommendationRepository;
    private final TelemetryFrameRepository telemetryFrameRepository;

    public RecommendationEngineService(RiskAssessmentRepository riskAssessmentRepository,
            AIRecommendationRepository aiRecommendationRepository,
            TelemetryFrameRepository telemetryFrameRepository) {
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.aiRecommendationRepository = aiRecommendationRepository;
        this.telemetryFrameRepository = telemetryFrameRepository;
    }

    @Transactional
    public List<AIRecommendation> generateRecommendations(UUID riskAssessmentId) {
        RiskAssessment riskAssessment = riskAssessmentRepository.findById(riskAssessmentId)
                .orElseThrow(() -> new IllegalArgumentException("RiskAssessment not found: " + riskAssessmentId));

        CognitiveState cogState = riskAssessment.getCognitiveState();
        TelemetryFrame frame = cogState.getTelemetryFrame();
        RiskLevel riskLevel = riskAssessment.getRiskLevel();
        PhaseOfFlight phase = frame.getPhaseOfFlight();

        log.info("[Rec] Generating recommendations for frame={} phase={} riskLevel={} swiss={}",
                frame.getFrameNumber(), phase, riskLevel, riskAssessment.isSwissCheeseTriggered());

        List<AIRecommendation> recommendations = new ArrayList<>();
        boolean anyRuleTriggered = false;

        // Rule 1: HIGH risk + autopilot not engaged → ENGAGE_AUTOPILOT
        if (riskLevel == RiskLevel.HIGH && !frame.isAutopilotEngaged()) {
            recommendations.add(build(riskAssessment,
                    RecommendationType.ENGAGE_AUTOPILOT,
                    Severity.WARNING,
                    10.0,
                    "High cognitive load detected. Engaging autopilot will reduce workload."));
            anyRuleTriggered = true;
        }

        // Rule 2: DESCENT or APPROACH + high vertical speed instability →
        // STABILIZE_DESCENT
        if ((phase == PhaseOfFlight.DESCENT || phase == PhaseOfFlight.APPROACH)
                && frame.getVerticalSpeedInstability() > 150.0) {
            recommendations.add(build(riskAssessment,
                    RecommendationType.STABILIZE_DESCENT,
                    Severity.WARNING,
                    7.0,
                    "Unstable vertical speed detected during " + phase + ". Stabilize descent rate."));
            anyRuleTriggered = true;
        }

        // Rule 3: Missed checklist probability > 0.5 → EXECUTE_CHECKLIST
        if (riskAssessment.getMissedChecklistProbability() > 0.5) {
            recommendations.add(build(riskAssessment,
                    RecommendationType.EXECUTE_CHECKLIST,
                    Severity.CAUTION,
                    5.0,
                    "High probability of missed checklist items. Review and execute checklist."));
            anyRuleTriggered = true;
        }

        // Rule 4: Swiss Cheese triggered → REDUCE_TASK_SWITCHING
        if (riskAssessment.isSwissCheeseTriggered()) {
            recommendations.add(build(riskAssessment,
                    RecommendationType.REDUCE_TASK_SWITCHING,
                    Severity.WARNING,
                    8.0,
                    "Multiple safety barriers breached simultaneously. Reduce task complexity immediately."));
            anyRuleTriggered = true;
        }

        // Rule 5: CRITICAL risk → GO_AROUND
        if (riskLevel == RiskLevel.CRITICAL) {
            recommendations.add(build(riskAssessment,
                    RecommendationType.GO_AROUND,
                    Severity.CRITICAL,
                    15.0,
                    "CRITICAL cognitive overload. Initiate go-around procedure immediately."));
            anyRuleTriggered = true;
        }

        // Rule 6: No rules triggered → MONITOR_ONLY
        if (!anyRuleTriggered) {
            recommendations.add(build(riskAssessment,
                    RecommendationType.MONITOR_ONLY,
                    Severity.INFO,
                    0.0,
                    "Cognitive load within acceptable limits. Continue monitoring."));
        }

        List<AIRecommendation> saved = aiRecommendationRepository.saveAll(recommendations);

        log.info("[Rec] Frame={} | {} recommendation(s) generated: {}",
                frame.getFrameNumber(),
                saved.size(),
                saved.stream().map(r -> r.getRecommendationType().name()).toList());

        return saved;
    }

    private AIRecommendation build(RiskAssessment riskAssessment,
            RecommendationType type,
            Severity severity,
            double reductionEstimate,
            String message) {
        log.debug("[Rec]   → {} | severity={} | reduction={}", type, severity, reductionEstimate);
        return AIRecommendation.builder()
                .riskAssessment(riskAssessment)
                .recommendationType(type)
                .severity(severity)
                .cognitiveLoadReductionEstimate(reductionEstimate)
                .message(message)
                .appliedVirtually(false)
                .timestamp(Instant.now())
                .build();
    }
}

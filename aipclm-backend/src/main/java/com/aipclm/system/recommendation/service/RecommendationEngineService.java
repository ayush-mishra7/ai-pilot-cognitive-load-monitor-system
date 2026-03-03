package com.aipclm.system.recommendation.service;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.model.RiskLevel;
import com.aipclm.system.recommendation.model.AIRecommendation;
import com.aipclm.system.recommendation.model.RecommendationType;
import com.aipclm.system.recommendation.model.Severity;
import com.aipclm.system.recommendation.repository.AIRecommendationRepository;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.risk.repository.RiskAssessmentRepository;
import com.aipclm.system.scenario.model.*;
import com.aipclm.system.scenario.repository.FlightScenarioRepository;
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
    private final FlightScenarioRepository scenarioRepository;

    public RecommendationEngineService(RiskAssessmentRepository riskAssessmentRepository,
            AIRecommendationRepository aiRecommendationRepository,
            TelemetryFrameRepository telemetryFrameRepository,
            FlightScenarioRepository scenarioRepository) {
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.aiRecommendationRepository = aiRecommendationRepository;
        this.telemetryFrameRepository = telemetryFrameRepository;
        this.scenarioRepository = scenarioRepository;
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

        /* ═══════════════════════════════════════════════
         *  SCENARIO-AWARE RULES (Phase 1)
         * ═══════════════════════════════════════════════ */
        UUID sessionId = frame.getFlightSession().getId();
        FlightScenario scenario = scenarioRepository.findByFlightSessionId(sessionId).orElse(null);

        if (scenario != null) {
            // Rule S1: Visibility ≤ LOW + APPROACH → REQUEST_ILS_APPROACH
            if ((scenario.getVisibility() == VisibilityLevel.LOW
                    || scenario.getVisibility() == VisibilityLevel.VERY_LOW
                    || scenario.getVisibility() == VisibilityLevel.ZERO)
                    && phase == PhaseOfFlight.APPROACH) {
                recommendations.add(build(riskAssessment,
                        RecommendationType.REQUEST_ILS_APPROACH,
                        Severity.WARNING,
                        8.0,
                        "Low visibility (" + scenario.getVisibility() + ") during approach. Request ILS approach clearance."));
                anyRuleTriggered = true;
            }

            // Rule S2: Emergency active + risk CRITICAL → DIVERT_TO_ALTERNATE
            if (scenario.getEmergencyType() != EmergencyType.NONE
                    && riskLevel == RiskLevel.CRITICAL) {
                recommendations.add(build(riskAssessment,
                        RecommendationType.DIVERT_TO_ALTERNATE,
                        Severity.CRITICAL,
                        20.0,
                        "Emergency " + scenario.getEmergencyType() + " with CRITICAL risk. Consider diverting to nearest alternate airport."));
                anyRuleTriggered = true;
            }

            // Rule S3: Crosswind > 25kt + LANDING → GO_AROUND_WEATHER
            if (scenario.getCrosswindComponent() > 25.0
                    && phase == PhaseOfFlight.LANDING) {
                recommendations.add(build(riskAssessment,
                        RecommendationType.GO_AROUND_WEATHER,
                        Severity.CRITICAL,
                        15.0,
                        "Crosswind component " + String.format("%.0f", scenario.getCrosswindComponent())
                                + " kt exceeds safe limits for landing. Execute go-around."));
                anyRuleTriggered = true;
            }

            // Rule S4: THUNDERSTORM + TAKEOFF → DELAY_TAKEOFF
            if (scenario.getWeatherCondition() == WeatherCondition.THUNDERSTORM
                    && phase == PhaseOfFlight.TAKEOFF) {
                recommendations.add(build(riskAssessment,
                        RecommendationType.DELAY_TAKEOFF,
                        Severity.WARNING,
                        12.0,
                        "Thunderstorm active during takeoff phase. Recommend delaying departure until weather improves."));
                anyRuleTriggered = true;
            }

            // Rule S5: Any emergency → SQUAWK_7700
            if (scenario.getEmergencyType() != EmergencyType.NONE) {
                recommendations.add(build(riskAssessment,
                        RecommendationType.SQUAWK_7700,
                        Severity.CRITICAL,
                        0.0,
                        "Emergency declared: " + scenario.getEmergencyType() + ". Set transponder to SQUAWK 7700."));
                anyRuleTriggered = true;
            }
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

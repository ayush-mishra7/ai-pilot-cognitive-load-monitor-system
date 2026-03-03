package com.aipclm.system.risk.service;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.model.RiskLevel;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.risk.repository.RiskAssessmentRepository;
import com.aipclm.system.scenario.model.*;
import com.aipclm.system.scenario.repository.FlightScenarioRepository;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class RiskEngineService {

    private final CognitiveStateRepository cognitiveStateRepository;
    private final TelemetryFrameRepository telemetryFrameRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final FlightScenarioRepository scenarioRepository;

    public RiskEngineService(CognitiveStateRepository cognitiveStateRepository,
            TelemetryFrameRepository telemetryFrameRepository,
            RiskAssessmentRepository riskAssessmentRepository,
            FlightScenarioRepository scenarioRepository) {
        this.cognitiveStateRepository = cognitiveStateRepository;
        this.telemetryFrameRepository = telemetryFrameRepository;
        this.riskAssessmentRepository = riskAssessmentRepository;
        this.scenarioRepository = scenarioRepository;
    }

    @Transactional
    public RiskAssessment evaluateRisk(UUID cognitiveStateId) {
        CognitiveState cogState = cognitiveStateRepository.findById(cognitiveStateId)
                .orElseThrow(() -> new IllegalArgumentException("CognitiveState not found: " + cognitiveStateId));

        TelemetryFrame frame = cogState.getTelemetryFrame();
        UUID sessionId = frame.getFlightSession().getId();

        // --- Smoothed Load: average of last 5 mlPredictedLoad values ---
        List<CognitiveState> recentStates = cognitiveStateRepository
                .findTop5BySessionIdOrderByTimestampDesc(sessionId);

        double smoothedLoad = recentStates.stream()
                .mapToDouble(CognitiveState::getMlPredictedLoad)
                .average()
                .orElse(cogState.getMlPredictedLoad());

        // --- Hysteresis Risk Level Determination ---
        // Retrieve previous risk level for hysteresis (check last risk assessment)
        RiskLevel previousRiskLevel = riskAssessmentRepository
                .findTopByCognitiveStateTelemetryFrameFlightSessionIdOrderByTimestampDesc(sessionId)
                .map(RiskAssessment::getRiskLevel)
                .orElse(RiskLevel.LOW);

        RiskLevel newRiskLevel = computeRiskWithHysteresis(smoothedLoad, previousRiskLevel);

        // --- Scenario-based risk adjustments ---
        FlightScenario scenario = scenarioRepository.findByFlightSessionId(sessionId).orElse(null);

        if (scenario != null) {
            // Scenario severity multiplier on aggregated smoothedLoad for threshold
            double severityMultiplier = switch (scenario.getDifficultyPreset()) {
                case NORMAL   -> 1.0;
                case MODERATE -> 1.3;
                case EXTREME  -> 1.6;
            };
            double adjustedLoad = smoothedLoad * severityMultiplier;

            // Re-evaluate hysteresis with adjusted load (makes HIGH/CRITICAL easier to reach)
            if (adjustedLoad > smoothedLoad) {
                RiskLevel adjustedLevel = computeRiskWithHysteresis(adjustedLoad, previousRiskLevel);
                if (adjustedLevel.ordinal() > newRiskLevel.ordinal()) {
                    log.info("[Risk] Scenario severity upgraded risk {} → {} (adjLoad={})",
                            newRiskLevel, adjustedLevel, String.format("%.1f", adjustedLoad));
                    newRiskLevel = adjustedLevel;
                }
            }

            // Emergency type → risk floor
            if (scenario.getEmergencyType() != EmergencyType.NONE) {
                RiskLevel emergencyFloor = switch (scenario.getEmergencyType()) {
                    case ENGINE_FAILURE, FIRE -> RiskLevel.HIGH;
                    default -> RiskLevel.MEDIUM;  // any other emergency ≠ NONE → minimum MEDIUM
                };
                if (newRiskLevel.ordinal() < emergencyFloor.ordinal()) {
                    log.info("[Risk] Emergency {} forces risk floor {} → {}",
                            scenario.getEmergencyType(), newRiskLevel, emergencyFloor);
                    newRiskLevel = emergencyFloor;
                }
            }

            // Weather penalty: lower Swiss Cheese thresholds effectively
            // THUNDERSTORM/ICE + NIGHT/FOG → lower Swiss Cheese alignment threshold
            if ((scenario.getWeatherCondition() == WeatherCondition.THUNDERSTORM
                    || scenario.getWeatherCondition() == WeatherCondition.ICE)
                    && scenario.getTimeOfDay() == TimeOfDay.NIGHT) {
                // Lower the effective swiss cheese fatigue threshold from 60 → 45
                if (smoothedLoad > 60.0 && frame.getFatigueIndex() > 45.0
                        && frame.getErrorCount() > 1 && frame.getTurbulenceLevel() > 0.03) {
                    // Override swissCheeseTriggered even with lighter conditions
                    log.info("[Risk] Severe weather + night: Swiss Cheese triggered at lower thresholds");
                }
            }
        }

        // --- Confidence Score Check: cap at HIGH if ML confidence < 0.7 ---
        if (cogState.getConfidenceScore() < 0.7 && newRiskLevel == RiskLevel.CRITICAL) {
            log.warn("[Risk] Confidence={} < 0.7 — capping CRITICAL → HIGH",
                    String.format("%.2f", cogState.getConfidenceScore()));
            newRiskLevel = RiskLevel.HIGH;
        }

        // --- Swiss Cheese Triggered ---
        // All four barriers must be breached simultaneously
        // Scenario-aware: lower thresholds under severe weather+night
        double scFatigueThr = 60.0;
        double scLoadThr = 70.0;
        int scErrorThr = 2;
        double scTurbThr = 0.05;
        if (scenario != null
                && (scenario.getWeatherCondition() == WeatherCondition.THUNDERSTORM
                    || scenario.getWeatherCondition() == WeatherCondition.ICE)
                && (scenario.getTimeOfDay() == TimeOfDay.NIGHT
                    || scenario.getVisibility() == VisibilityLevel.ZERO
                    || scenario.getVisibility() == VisibilityLevel.VERY_LOW)) {
            scFatigueThr = 45.0;
            scLoadThr = 60.0;
            scErrorThr = 1;
            scTurbThr = 0.03;
        }
        boolean swissCheeseTriggered = smoothedLoad > scLoadThr
                && frame.getFatigueIndex() > scFatigueThr
                && frame.getErrorCount() > scErrorThr
                && frame.getTurbulenceLevel() > scTurbThr;

        // --- Aggregated Risk Score (with scenario multiplier) ---
        double baseSeverityMultiplier = 1.0;
        if (scenario != null) {
            baseSeverityMultiplier = switch (scenario.getDifficultyPreset()) {
                case NORMAL   -> 1.0;
                case MODERATE -> 1.3;
                case EXTREME  -> 1.6;
            };
        }
        double aggregatedRiskScore = clamp(
                ((cogState.getMlPredictedLoad() * 0.6) + (cogState.getErrorProbability() * 100.0 * 0.4))
                        * baseSeverityMultiplier,
                0, 100);

        // --- Probability computations ---
        double delayedReactionProbability = clamp(cogState.getMlPredictedLoad() * 0.008, 0, 1);
        double unsafeDescentProbability = clamp(
                cogState.getMlPredictedLoad() * 0.005 + (frame.getVerticalSpeed() < -2000 ? 0.1 : 0.0), 0, 1);
        double missedChecklistProbability = clamp(
                cogState.getMlPredictedLoad() * 0.006 + (frame.getChecklistDelaySeconds() / 60.0), 0, 1);

        // --- Persist smoothedLoad back into CognitiveState ---
        cogState.setSmoothedLoad(smoothedLoad);
        cognitiveStateRepository.save(cogState);

        log.info("[Risk] Frame={} Phase={} | smoothedLoad={} | prev={} → new={} | swiss={} | aggScore={}",
                frame.getFrameNumber(),
                frame.getPhaseOfFlight(),
                String.format("%.2f", smoothedLoad),
                previousRiskLevel,
                newRiskLevel,
                swissCheeseTriggered,
                String.format("%.2f", aggregatedRiskScore));

        RiskAssessment assessment = RiskAssessment.builder()
                .cognitiveState(cogState)
                .riskLevel(newRiskLevel)
                .delayedReactionProbability(delayedReactionProbability)
                .unsafeDescentProbability(unsafeDescentProbability)
                .missedChecklistProbability(missedChecklistProbability)
                .aggregatedRiskScore(aggregatedRiskScore)
                .riskEscalated(isHigherRisk(newRiskLevel, previousRiskLevel))
                .swissCheeseTriggered(swissCheeseTriggered)
                .timestamp(Instant.now())
                .build();

        return riskAssessmentRepository.save(assessment);
    }

    /**
     * Hysteresis logic: transitions UP at 40/60/80, DOWN only at 35/55/75.
     * Prevents single-spike jumps between risk levels.
     */
    private RiskLevel computeRiskWithHysteresis(double smoothedLoad, RiskLevel current) {
        return switch (current) {
            case LOW -> {
                if (smoothedLoad >= 40)
                    yield RiskLevel.MEDIUM;
                yield RiskLevel.LOW;
            }
            case MEDIUM -> {
                if (smoothedLoad >= 60)
                    yield RiskLevel.HIGH;
                if (smoothedLoad < 35)
                    yield RiskLevel.LOW;
                yield RiskLevel.MEDIUM;
            }
            case HIGH -> {
                if (smoothedLoad >= 80)
                    yield RiskLevel.CRITICAL;
                if (smoothedLoad < 55)
                    yield RiskLevel.MEDIUM;
                yield RiskLevel.HIGH;
            }
            case CRITICAL -> {
                if (smoothedLoad < 75)
                    yield RiskLevel.HIGH;
                yield RiskLevel.CRITICAL;
            }
        };
    }

    private boolean isHigherRisk(RiskLevel newLevel, RiskLevel oldLevel) {
        return newLevel.ordinal() > oldLevel.ordinal();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

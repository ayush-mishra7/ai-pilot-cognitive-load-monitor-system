package com.aipclm.system.cognitive.service;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.model.RiskLevel;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Phase 3 — Advanced Cognitive Load Pipeline
 * ============================================
 * 1. Expert CLI (5-component weighted formula)
 * 2. ML Inference (trained GradientBoosting model)
 * 3. Confidence-Weighted Fusion:  fused = conf * ml + (1-conf) * expert
 * 4. EMA Smoothed Load:  smoothed = α * fused + (1-α) * prev_smoothed
 * 5. Fatigue Trend Slope:  linear regression on last 10 fatigue values
 * 6. Swiss Cheese Alignment Score:  multi-barrier breach assessment
 */
@Service
@Slf4j
public class CognitiveLoadService {

        /** EMA smoothing factor — higher = more responsive, lower = smoother */
        private static final double EMA_ALPHA = 0.3;

        /** Window size for fatigue trend slope computation */
        private static final int FATIGUE_TREND_WINDOW = 10;

        /** Swiss Cheese barrier thresholds */
        private static final double BARRIER_LOAD_THRESHOLD = 70.0;
        private static final double BARRIER_FATIGUE_THRESHOLD = 60.0;
        private static final int BARRIER_ERROR_THRESHOLD = 2;
        private static final double BARRIER_TURBULENCE_THRESHOLD = 0.05;
        private static final int SWISS_CHEESE_TOTAL_BARRIERS = 4;

        private final TelemetryFrameRepository telemetryFrameRepository;
        private final CognitiveStateRepository cognitiveStateRepository;
        private final MLInferenceService mlInferenceService;

        public CognitiveLoadService(TelemetryFrameRepository telemetryFrameRepository,
                        CognitiveStateRepository cognitiveStateRepository,
                        MLInferenceService mlInferenceService) {
                this.telemetryFrameRepository = telemetryFrameRepository;
                this.cognitiveStateRepository = cognitiveStateRepository;
                this.mlInferenceService = mlInferenceService;
        }

        @Transactional
        public double computeCognitiveLoad(UUID telemetryFrameId) {
                TelemetryFrame frame = telemetryFrameRepository.findById(telemetryFrameId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "TelemetryFrame not found: " + telemetryFrameId));

                UUID sessionId = frame.getFlightSession().getId();

                // ── Step 1: Expert CLI (5-component weighted formula) ──
                double expertComputedLoad = computeExpertCLI(frame);

                // ── Step 2: ML Inference ──
                MLPredictionResponse mlResponse = mlInferenceService.callPredictionAPI(frame, expertComputedLoad);
                double mlPredictedLoad = clamp(mlResponse.getPredictedLoad(), 0, 100);
                double errorProbability = clamp(mlResponse.getErrorProbability(), 0, 1);
                double confidenceScore = clamp(mlResponse.getConfidenceScore(), 0, 1);

                // ── Step 3: Confidence-Weighted Fusion ──
                double fusedLoad = computeFusedLoad(expertComputedLoad, mlPredictedLoad, confidenceScore);

                // ── Step 4: EMA Smoothed Load ──
                List<CognitiveState> recentStates = cognitiveStateRepository
                                .findRecentBySessionId(sessionId, FATIGUE_TREND_WINDOW);
                // Reverse to oldest-first for time-series processing
                List<CognitiveState> orderedStates = new ArrayList<>(recentStates);
                Collections.reverse(orderedStates);

                double smoothedLoad = computeEMASmoothedLoad(fusedLoad, orderedStates);

                // ── Step 5: Fatigue Trend Slope ──
                double fatigueTrendSlope = computeFatigueTrendSlope(frame.getFatigueIndex(), orderedStates);

                // ── Step 6: Swiss Cheese Alignment Score ──
                double swissCheeseScore = computeSwissCheeseScore(fusedLoad, frame);

                log.info("[CLI Frame={}] expert={} ml={} conf={} fused={} smoothed={} fatigueTrend={} swissCheese={}",
                                frame.getFrameNumber(),
                                String.format("%.2f", expertComputedLoad),
                                String.format("%.2f", mlPredictedLoad),
                                String.format("%.2f", confidenceScore),
                                String.format("%.2f", fusedLoad),
                                String.format("%.2f", smoothedLoad),
                                String.format("%.4f", fatigueTrendSlope),
                                String.format("%.2f", swissCheeseScore));

                // ── Persist CognitiveState ──
                CognitiveState cognitiveState = CognitiveState.builder()
                                .telemetryFrame(frame)
                                .expertComputedLoad(expertComputedLoad)
                                .mlPredictedLoad(mlPredictedLoad)
                                .errorProbability(errorProbability)
                                .confidenceScore(confidenceScore)
                                .smoothedLoad(smoothedLoad)
                                .fatigueTrendSlope(fatigueTrendSlope)
                                .swissCheeseAlignmentScore(swissCheeseScore)
                                .advisoryGenerated(false)
                                .riskLevel(RiskLevel.LOW)
                                .timestamp(Instant.now())
                                .build();

                cognitiveStateRepository.save(cognitiveState);

                return expertComputedLoad;
        }

        // ══════════════════════════════════════════════════════════════════
        //  Expert CLI — 5-component weighted formula (unchanged from Phase 1)
        // ══════════════════════════════════════════════════════════════════

        private double computeExpertCLI(TelemetryFrame frame) {
                double phaseBoost = switch (frame.getPhaseOfFlight()) {
                        case APPROACH, LANDING -> 1.4;
                        case TAKEOFF -> 1.2;
                        case DESCENT -> 1.1;
                        case CLIMB -> 1.0;
                        case CRUISE -> 0.8;
                };

                double normalizedReaction = clamp((frame.getReactionTimeMs() - 200.0) / (1500.0 - 200.0) * 100.0, 0, 100);
                double normalizedErrors = clamp(frame.getErrorCount() / 10.0 * 100.0, 0, 100);
                double taskComplexity = clamp(phaseBoost * ((normalizedReaction * 0.6) + (normalizedErrors * 0.4)), 0, 100);

                double normalizedTurbulence = clamp(frame.getTurbulenceLevel() * 100.0, 0, 100);
                double normalizedWeather = clamp(frame.getWeatherSeverity() * 100.0, 0, 100);
                double environmentalStress = clamp((normalizedTurbulence * 0.6) + (normalizedWeather * 0.4), 0, 100);

                double normalizedJitter = clamp(frame.getControlJitterIndex() * 100.0, 0, 100);
                double normalizedChecklist = clamp(frame.getChecklistDelaySeconds() / 30.0 * 100.0, 0, 100);
                double normalizedSwitchRate = clamp(frame.getTaskSwitchRate() / 10.0 * 100.0, 0, 100);
                double behavioralStrain = clamp(
                                (normalizedJitter * 0.4) + (normalizedChecklist * 0.35) + (normalizedSwitchRate * 0.25), 0, 100);

                double normalizedStress = clamp(frame.getStressIndex(), 0, 100);
                double normalizedHeartRate = clamp((frame.getHeartRate() - 60.0) / (160.0 - 60.0) * 100.0, 0, 100);
                double physiologicalStrain = clamp((normalizedStress * 0.6) + (normalizedHeartRate * 0.4), 0, 100);

                double normalizedFatigue = clamp(frame.getFatigueIndex(), 0, 100);
                double normalizedBlinkFatigue = clamp((30.0 - frame.getBlinkRate()) / 20.0 * 100.0, 0, 100);
                double fatigueComponent = clamp((normalizedFatigue * 0.7) + (normalizedBlinkFatigue * 0.3), 0, 100);

                return clamp(
                        (taskComplexity * 0.25) +
                        (environmentalStress * 0.20) +
                        (behavioralStrain * 0.20) +
                        (physiologicalStrain * 0.15) +
                        (fatigueComponent * 0.20), 0, 100);
        }

        // ══════════════════════════════════════════════════════════════════
        //  Confidence-Weighted Fusion
        // ══════════════════════════════════════════════════════════════════

        /**
         * Fuses expert and ML predictions using confidence as the weight.
         * Higher confidence → more weight to ML.
         * fused = confidence * mlLoad + (1 - confidence) * expertLoad
         */
        private double computeFusedLoad(double expertLoad, double mlLoad, double confidence) {
                double fused = (confidence * mlLoad) + ((1.0 - confidence) * expertLoad);
                return clamp(fused, 0, 100);
        }

        // ══════════════════════════════════════════════════════════════════
        //  EMA Smoothed Load
        // ══════════════════════════════════════════════════════════════════

        /**
         * Exponential Moving Average: smoothed = α * current + (1 - α) * previous.
         * If no history exists, uses current value as the seed.
         */
        private double computeEMASmoothedLoad(double currentFusedLoad, List<CognitiveState> orderedStates) {
                if (orderedStates.isEmpty()) {
                        return currentFusedLoad;
                }
                // Get the most recent smoothed load (last element in ordered list = most recent)
                double prevSmoothed = orderedStates.get(orderedStates.size() - 1).getSmoothedLoad();
                if (prevSmoothed <= 0.0 && orderedStates.size() == 1) {
                        // First frame had no EMA — seed with current
                        return currentFusedLoad;
                }
                return clamp(EMA_ALPHA * currentFusedLoad + (1.0 - EMA_ALPHA) * prevSmoothed, 0, 100);
        }

        // ══════════════════════════════════════════════════════════════════
        //  Fatigue Trend Slope (linear regression on fatigue index window)
        // ══════════════════════════════════════════════════════════════════

        /**
         * Computes the slope of fatigue values over the last N frames using
         * ordinary least squares linear regression.
         * Positive slope → fatigue increasing. Negative → decreasing.
         * Value represents fatigue units per frame.
         */
        private double computeFatigueTrendSlope(double currentFatigue, List<CognitiveState> orderedStates) {
                List<Double> fatigueValues = new ArrayList<>();
                for (CognitiveState state : orderedStates) {
                        fatigueValues.add(state.getTelemetryFrame().getFatigueIndex());
                }
                fatigueValues.add(currentFatigue);

                if (fatigueValues.size() < 2) {
                        return 0.0;
                }

                int n = fatigueValues.size();
                double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
                for (int i = 0; i < n; i++) {
                        double x = i;
                        double y = fatigueValues.get(i);
                        sumX += x;
                        sumY += y;
                        sumXY += x * y;
                        sumX2 += x * x;
                }

                double denominator = n * sumX2 - sumX * sumX;
                if (Math.abs(denominator) < 1e-10) {
                        return 0.0;
                }

                return (n * sumXY - sumX * sumY) / denominator;
        }

        // ══════════════════════════════════════════════════════════════════
        //  Swiss Cheese Alignment Score
        // ══════════════════════════════════════════════════════════════════

        /**
         * Swiss Cheese Model: each "barrier" is an independent safety layer.
         * When multiple barriers are breached simultaneously, the "holes align"
         * and risk escalates. Score = breached barriers / total barriers (0-1).
         *
         * Barriers:
         * 1. Cognitive Load > 70  (overload threshold)
         * 2. Fatigue > 60        (human factors limit)
         * 3. Errors > 2          (performance degradation indicator)
         * 4. Turbulence > 0.05   (environmental hazard)
         */
        private double computeSwissCheeseScore(double fusedLoad, TelemetryFrame frame) {
                int breached = 0;

                if (fusedLoad > BARRIER_LOAD_THRESHOLD) breached++;
                if (frame.getFatigueIndex() > BARRIER_FATIGUE_THRESHOLD) breached++;
                if (frame.getErrorCount() > BARRIER_ERROR_THRESHOLD) breached++;
                if (frame.getTurbulenceLevel() > BARRIER_TURBULENCE_THRESHOLD) breached++;

                return (double) breached / SWISS_CHEESE_TOTAL_BARRIERS;
        }

        private double clamp(double value, double min, double max) {
                return Math.max(min, Math.min(max, value));
        }
}

package com.aipclm.system.cognitive.service;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.model.RiskLevel;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.telemetry.model.PhaseOfFlight;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class CognitiveLoadService {

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

                // --- Component 1: TaskComplexity (weight 0.25) ---
                double phaseBoost = switch (frame.getPhaseOfFlight()) {
                        case APPROACH, LANDING -> 1.4;
                        case TAKEOFF -> 1.2;
                        case DESCENT -> 1.1;
                        case CLIMB -> 1.0;
                        case CRUISE -> 0.8;
                };
                double normalizedReaction = clamp((frame.getReactionTimeMs() - 200.0) / (1500.0 - 200.0) * 100.0, 0,
                                100);
                double normalizedErrors = clamp(frame.getErrorCount() / 10.0 * 100.0, 0, 100);
                double taskComplexity = clamp(phaseBoost * ((normalizedReaction * 0.6) + (normalizedErrors * 0.4)), 0,
                                100);

                // --- Component 2: EnvironmentalStress (weight 0.20) ---
                double normalizedTurbulence = clamp(frame.getTurbulenceLevel() * 100.0, 0, 100);
                double normalizedWeather = clamp(frame.getWeatherSeverity() * 100.0, 0, 100);
                double environmentalStress = clamp((normalizedTurbulence * 0.6) + (normalizedWeather * 0.4), 0, 100);

                // --- Component 3: BehavioralStrain (weight 0.20) ---
                double normalizedJitter = clamp(frame.getControlJitterIndex() * 100.0, 0, 100);
                double normalizedChecklist = clamp(frame.getChecklistDelaySeconds() / 30.0 * 100.0, 0, 100);
                double normalizedSwitchRate = clamp(frame.getTaskSwitchRate() / 10.0 * 100.0, 0, 100);
                double behavioralStrain = clamp(
                                (normalizedJitter * 0.4) + (normalizedChecklist * 0.35) + (normalizedSwitchRate * 0.25),
                                0, 100);

                // --- Component 4: PhysiologicalStrain (weight 0.15) ---
                double normalizedStress = clamp(frame.getStressIndex(), 0, 100);
                double normalizedHeartRate = clamp((frame.getHeartRate() - 60.0) / (160.0 - 60.0) * 100.0, 0, 100);
                double physiologicalStrain = clamp((normalizedStress * 0.6) + (normalizedHeartRate * 0.4), 0, 100);

                // --- Component 5: FatigueComponent (weight 0.20) ---
                double normalizedFatigue = clamp(frame.getFatigueIndex(), 0, 100);
                double normalizedBlinkFatigue = clamp((30.0 - frame.getBlinkRate()) / 20.0 * 100.0, 0, 100);
                double fatigueComponent = clamp((normalizedFatigue * 0.7) + (normalizedBlinkFatigue * 0.3), 0, 100);

                // --- Final Weighted Expert CLI ---
                double expertComputedLoad = clamp(
                                (taskComplexity * 0.25) +
                                                (environmentalStress * 0.20) +
                                                (behavioralStrain * 0.20) +
                                                (physiologicalStrain * 0.15) +
                                                (fatigueComponent * 0.20),
                                0, 100);

                log.info("[CLI Frame={}] Phase={} | TaskComplexity={} | EnvStress={} | BehavStrain={} | PhysioStrain={} | Fatigue={} | expertCLI={}",
                                frame.getFrameNumber(),
                                frame.getPhaseOfFlight(),
                                String.format("%.1f", taskComplexity),
                                String.format("%.1f", environmentalStress),
                                String.format("%.1f", behavioralStrain),
                                String.format("%.1f", physiologicalStrain),
                                String.format("%.1f", fatigueComponent),
                                String.format("%.2f", expertComputedLoad));

                // --- ML Inference ---
                MLPredictionResponse mlResponse = mlInferenceService.callPredictionAPI(frame, expertComputedLoad);

                double mlPredictedLoad = clamp(mlResponse.getPredictedLoad(), 0, 100);
                double errorProbability = clamp(mlResponse.getErrorProbability(), 0, 1);
                double confidenceScore = clamp(mlResponse.getConfidenceScore(), 0, 1);

                log.info("[CLI Frame={}] expertLoad={} | mlPredictedLoad={} | errorProb={} | confidence={}",
                                frame.getFrameNumber(),
                                String.format("%.2f", expertComputedLoad),
                                String.format("%.2f", mlPredictedLoad),
                                String.format("%.4f", errorProbability),
                                String.format("%.2f", confidenceScore));

                // --- Persist CognitiveState ---
                CognitiveState cognitiveState = CognitiveState.builder()
                                .telemetryFrame(frame)
                                .expertComputedLoad(expertComputedLoad)
                                .mlPredictedLoad(mlPredictedLoad)
                                .errorProbability(errorProbability)
                                .confidenceScore(confidenceScore)
                                .smoothedLoad(0.0)
                                .fatigueTrendSlope(0.0)
                                .swissCheeseAlignmentScore(0.0)
                                .advisoryGenerated(false)
                                .riskLevel(RiskLevel.LOW)
                                .timestamp(Instant.now())
                                .build();

                cognitiveStateRepository.save(cognitiveState);

                return expertComputedLoad;
        }

        private double clamp(double value, double min, double max) {
                return Math.max(min, Math.min(max, value));
        }
}

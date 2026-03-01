package com.aipclm.system.simulation.service;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.cognitive.service.CognitiveLoadService;
import com.aipclm.system.recommendation.model.AIRecommendation;
import com.aipclm.system.recommendation.service.RecommendationEngineService;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.risk.repository.RiskAssessmentRepository;
import com.aipclm.system.risk.service.RiskEngineService;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class SimulationOrchestratorService {

        private final SimulationEngineService simulationEngineService;
        private final CognitiveLoadService cognitiveLoadService;
        private final RiskEngineService riskEngineService;
        private final RecommendationEngineService recommendationEngineService;
        private final TelemetryFrameRepository telemetryFrameRepository;
        private final CognitiveStateRepository cognitiveStateRepository;
        private final RiskAssessmentRepository riskAssessmentRepository;

        public SimulationOrchestratorService(SimulationEngineService simulationEngineService,
                        CognitiveLoadService cognitiveLoadService,
                        RiskEngineService riskEngineService,
                        RecommendationEngineService recommendationEngineService,
                        TelemetryFrameRepository telemetryFrameRepository,
                        CognitiveStateRepository cognitiveStateRepository,
                        RiskAssessmentRepository riskAssessmentRepository) {
                this.simulationEngineService = simulationEngineService;
                this.cognitiveLoadService = cognitiveLoadService;
                this.riskEngineService = riskEngineService;
                this.recommendationEngineService = recommendationEngineService;
                this.telemetryFrameRepository = telemetryFrameRepository;
                this.cognitiveStateRepository = cognitiveStateRepository;
                this.riskAssessmentRepository = riskAssessmentRepository;
        }

        /**
         * Runs a single full co-pilot pipeline step for the given session:
         * Simulation → Expert CLI → ML Inference → Risk Engine → Recommendations.
         * Each stage is logged. Null checks are enforced — any missing record throws
         * an IllegalStateException to prevent silent failures.
         */
        @Transactional(rollbackFor = Exception.class)
        public SimulationStepResult runSingleSimulationStep(UUID sessionId) {
                log.info("[Orchestrator] ======== STARTING PIPELINE STEP FOR SESSION={} ========", sessionId);

                try {
                        // Stage 1: Generate next telemetry frame
                        simulationEngineService.generateNextFrame(sessionId);

                        TelemetryFrame frame = telemetryFrameRepository
                                        .findTopByFlightSessionIdOrderByFrameNumberDesc(sessionId)
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "No TelemetryFrame found after generation for session="
                                                                        + sessionId));

                        log.info("[Orchestrator] Stage 1 ✓ Frame={} Phase={}", frame.getFrameNumber(),
                                        frame.getPhaseOfFlight());

                        // Stage 2: Compute cognitive load (expert + ML inference → saves
                        // CognitiveState)
                        double expertLoad = cognitiveLoadService.computeCognitiveLoad(frame.getId());

                        CognitiveState cogState = cognitiveStateRepository
                                        .findByTelemetryFrameId(frame.getId())
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "No CognitiveState found after CLI computation for frame="
                                                                        + frame.getId()));

                        log.info("[Orchestrator] Stage 2 ✓ expertLoad={} mlLoad={} confidence={}",
                                        String.format("%.2f", expertLoad),
                                        String.format("%.2f", cogState.getMlPredictedLoad()),
                                        String.format("%.2f", cogState.getConfidenceScore()));

                        // Stage 3: Evaluate risk (hysteresis + Swiss Cheese → saves RiskAssessment)
                        RiskAssessment riskAssessment = riskEngineService.evaluateRisk(cogState.getId());

                        if (riskAssessment == null) {
                                throw new IllegalStateException("RiskEngineService returned null for cognitiveStateId="
                                                + cogState.getId());
                        }

                        log.info("[Orchestrator] Stage 3 ✓ riskLevel={} swiss={} aggScore={}",
                                        riskAssessment.getRiskLevel(),
                                        riskAssessment.isSwissCheeseTriggered(),
                                        String.format("%.2f", riskAssessment.getAggregatedRiskScore()));

                        // Stage 4: Generate recommendations (rule-based → saves AIRecommendation
                        // records)
                        List<AIRecommendation> recommendations = recommendationEngineService
                                        .generateRecommendations(riskAssessment.getId());

                        if (recommendations == null || recommendations.isEmpty()) {
                                throw new IllegalStateException(
                                                "No recommendations generated for riskAssessmentId="
                                                                + riskAssessment.getId());
                        }

                        log.info("[Orchestrator] Stage 4 ✓ {} recommendation(s): {}",
                                        recommendations.size(),
                                        recommendations.stream().map(r -> r.getRecommendationType().name()).toList());

                        log.info("[Orchestrator] ======== PIPELINE STEP COMPLETE: FRAME={} RISK={} RECS={} ========",
                                        frame.getFrameNumber(), riskAssessment.getRiskLevel(), recommendations.size());

                        return new SimulationStepResult(
                                        frame.getFrameNumber(),
                                        frame.getPhaseOfFlight().name(),
                                        expertLoad,
                                        cogState.getMlPredictedLoad(),
                                        cogState.getConfidenceScore(),
                                        riskAssessment.getRiskLevel().name(),
                                        riskAssessment.isSwissCheeseTriggered(),
                                        riskAssessment.getAggregatedRiskScore(),
                                        recommendations.stream().map(r -> r.getRecommendationType().name()).toList());

                } catch (Exception e) {
                        log.error("[Orchestrator] ======== PIPELINE STEP FAILED: SESSION={} REASON={} ========",
                                        sessionId, e.getMessage());
                        throw e; // Propagate up to trigger the @Transactional rollback
                }
        }

        /** Immutable result DTO returned from a single orchestrated pipeline step. */
        public record SimulationStepResult(
                        int frameNumber,
                        String phase,
                        double expertLoad,
                        double mlPredictedLoad,
                        double confidenceScore,
                        String riskLevel,
                        boolean swissCheeseTriggered,
                        double aggregatedRiskScore,
                        List<String> recommendations) {
        }
}

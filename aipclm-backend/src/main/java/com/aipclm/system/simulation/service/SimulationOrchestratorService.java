package com.aipclm.system.simulation.service;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.cognitive.service.CognitiveLoadService;
import com.aipclm.system.crm.model.CrmAssessment;
import com.aipclm.system.crm.service.CrmService;
import com.aipclm.system.pilot.model.CrewRole;
import com.aipclm.system.recommendation.model.AIRecommendation;
import com.aipclm.system.recommendation.service.RecommendationEngineService;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.risk.repository.RiskAssessmentRepository;
import com.aipclm.system.risk.service.RiskEngineService;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.repository.FlightSessionRepository;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
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
        private final FlightSessionRepository flightSessionRepository;
        private final CrmService crmService;
        private final Counter pipelineStepCounter;
        private final Counter pipelineFailureCounter;
        private final Timer pipelineStepTimer;

        public SimulationOrchestratorService(SimulationEngineService simulationEngineService,
                        CognitiveLoadService cognitiveLoadService,
                        RiskEngineService riskEngineService,
                        RecommendationEngineService recommendationEngineService,
                        TelemetryFrameRepository telemetryFrameRepository,
                        CognitiveStateRepository cognitiveStateRepository,
                        RiskAssessmentRepository riskAssessmentRepository,
                        FlightSessionRepository flightSessionRepository,
                        CrmService crmService,
                        Counter pipelineStepCounter,
                        Counter pipelineFailureCounter,
                        Timer pipelineStepTimer) {
                this.simulationEngineService = simulationEngineService;
                this.cognitiveLoadService = cognitiveLoadService;
                this.riskEngineService = riskEngineService;
                this.recommendationEngineService = recommendationEngineService;
                this.telemetryFrameRepository = telemetryFrameRepository;
                this.cognitiveStateRepository = cognitiveStateRepository;
                this.riskAssessmentRepository = riskAssessmentRepository;
                this.flightSessionRepository = flightSessionRepository;
                this.crmService = crmService;
                this.pipelineStepCounter = pipelineStepCounter;
                this.pipelineFailureCounter = pipelineFailureCounter;
                this.pipelineStepTimer = pipelineStepTimer;
        }

        /**
         * Runs a single full co-pilot pipeline step for the given session:
         * Simulation → Expert CLI → ML Inference → Risk Engine → Recommendations.
         * Each stage is logged. Null checks are enforced — any missing record throws
         * an IllegalStateException to prevent silent failures.
         */
        @Transactional(rollbackFor = Exception.class)
        public SimulationStepResult runSingleSimulationStep(UUID sessionId) {
                return pipelineStepTimer.record(() -> {
                        pipelineStepCounter.increment();
                        return doRunSingleSimulationStep(sessionId);
                });
        }

        private SimulationStepResult doRunSingleSimulationStep(UUID sessionId) {
                log.info("[Orchestrator] ======== STARTING PIPELINE STEP FOR SESSION={} ========", sessionId);

                FlightSession session = flightSessionRepository.findById(sessionId)
                        .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));

                if (session.isCrewMode()) {
                        return runCrewSimulationStep(sessionId);
                }

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
                                        recommendations.stream().map(r -> r.getRecommendationType().name()).toList(),
                                        false, null);

                } catch (Exception e) {
                        pipelineFailureCounter.increment();
                        log.error("[Orchestrator] ======== PIPELINE STEP FAILED: SESSION={} REASON={} ========",
                                        sessionId, e.getMessage());
                        throw e;
                }
        }

        /**
         * Crew-mode pipeline: generates two frames (Captain + FO), computes cognitive
         * load for both, evaluates CRM, then runs risk + recommendations on the
         * Pilot Flying's cognitive state.
         */
        private SimulationStepResult runCrewSimulationStep(UUID sessionId) {
                try {
                        // Stage 1: Generate crew frames (Captain + FO)
                        simulationEngineService.generateCrewFrames(sessionId);

                        TelemetryFrame captainFrame = telemetryFrameRepository
                                        .findTopByFlightSessionIdAndCrewRoleOrderByFrameNumberDesc(sessionId, CrewRole.CAPTAIN)
                                        .orElseThrow(() -> new IllegalStateException("No Captain frame for session=" + sessionId));
                        TelemetryFrame foFrame = telemetryFrameRepository
                                        .findTopByFlightSessionIdAndCrewRoleOrderByFrameNumberDesc(sessionId, CrewRole.FIRST_OFFICER)
                                        .orElseThrow(() -> new IllegalStateException("No FO frame for session=" + sessionId));

                        log.info("[Orchestrator] Stage 1 ✓ CREW Frame={} Phase={}", captainFrame.getFrameNumber(),
                                        captainFrame.getPhaseOfFlight());

                        // Stage 2: Compute cognitive load for BOTH crew members
                        double captainExpertLoad = cognitiveLoadService.computeCognitiveLoad(captainFrame.getId());
                        double foExpertLoad = cognitiveLoadService.computeCognitiveLoad(foFrame.getId());

                        CognitiveState captainCog = cognitiveStateRepository
                                        .findByTelemetryFrameId(captainFrame.getId())
                                        .orElseThrow(() -> new IllegalStateException("No CognitiveState for Captain frame"));
                        CognitiveState foCog = cognitiveStateRepository
                                        .findByTelemetryFrameId(foFrame.getId())
                                        .orElseThrow(() -> new IllegalStateException("No CognitiveState for FO frame"));

                        log.info("[Orchestrator] Stage 2 ✓ Captain ML={} FO ML={}",
                                        String.format("%.2f", captainCog.getMlPredictedLoad()),
                                        String.format("%.2f", foCog.getMlPredictedLoad()));

                        // Stage 3: CRM assessment
                        CrmAssessment crmAssessment = crmService.evaluateCrm(sessionId, captainFrame.getFrameNumber());

                        log.info("[Orchestrator] Stage 3 ✓ CRM effectiveness={} comm={}",
                                        String.format("%.1f", crmAssessment.getCrmEffectivenessScore()),
                                        String.format("%.1f", crmAssessment.getCommunicationScore()));

                        // Stage 4: Risk evaluation — use the HIGHER cognitive load (worst-case crew member)
                        CognitiveState primaryCog = captainCog.getSmoothedLoad() >= foCog.getSmoothedLoad()
                                        ? captainCog : foCog;
                        RiskAssessment riskAssessment = riskEngineService.evaluateRisk(primaryCog.getId());

                        if (riskAssessment == null) {
                                throw new IllegalStateException("RiskEngineService returned null for crew session");
                        }

                        log.info("[Orchestrator] Stage 4 ✓ riskLevel={} swiss={}", riskAssessment.getRiskLevel(),
                                        riskAssessment.isSwissCheeseTriggered());

                        // Stage 5: Recommendations
                        List<AIRecommendation> recommendations = recommendationEngineService
                                        .generateRecommendations(riskAssessment.getId());

                        if (recommendations == null || recommendations.isEmpty()) {
                                throw new IllegalStateException("No recommendations generated for crew session");
                        }

                        log.info("[Orchestrator] ======== CREW PIPELINE COMPLETE: FRAME={} CRM={} RISK={} ========",
                                        captainFrame.getFrameNumber(),
                                        String.format("%.0f", crmAssessment.getCrmEffectivenessScore()),
                                        riskAssessment.getRiskLevel());

                        CrmStepData crmData = new CrmStepData(
                                        crmAssessment.getCommunicationScore(),
                                        crmAssessment.getWorkloadDistribution(),
                                        crmAssessment.getAuthorityGradient(),
                                        crmAssessment.getSituationalAwarenessScore(),
                                        crmAssessment.getCrmEffectivenessScore(),
                                        crmAssessment.getFatigueSymmetry(),
                                        crmAssessment.getCaptainLoad(),
                                        crmAssessment.getFirstOfficerLoad());

                        return new SimulationStepResult(
                                        captainFrame.getFrameNumber(),
                                        captainFrame.getPhaseOfFlight().name(),
                                        captainExpertLoad,
                                        captainCog.getMlPredictedLoad(),
                                        captainCog.getConfidenceScore(),
                                        riskAssessment.getRiskLevel().name(),
                                        riskAssessment.isSwissCheeseTriggered(),
                                        riskAssessment.getAggregatedRiskScore(),
                                        recommendations.stream().map(r -> r.getRecommendationType().name()).toList(),
                                        true, crmData);

                } catch (Exception e) {
                        log.error("[Orchestrator] ======== CREW PIPELINE FAILED: SESSION={} REASON={} ========",
                                        sessionId, e.getMessage());
                        throw e;
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
                        List<String> recommendations,
                        boolean crewMode,
                        CrmStepData crmData) {
        }

        /** CRM metrics returned when crewMode=true. */
        public record CrmStepData(
                        double communicationScore,
                        double workloadDistribution,
                        double authorityGradient,
                        double situationalAwareness,
                        double crmEffectiveness,
                        double fatigueSymmetry,
                        double captainLoad,
                        double firstOfficerLoad) {
        }
}

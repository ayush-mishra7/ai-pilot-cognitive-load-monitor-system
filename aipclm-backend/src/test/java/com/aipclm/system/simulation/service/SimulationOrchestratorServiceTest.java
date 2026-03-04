package com.aipclm.system.simulation.service;

import com.aipclm.system.TestFixtures;
import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.model.RiskLevel;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.cognitive.service.CognitiveLoadService;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.recommendation.model.AIRecommendation;
import com.aipclm.system.recommendation.model.RecommendationType;
import com.aipclm.system.recommendation.model.Severity;
import com.aipclm.system.recommendation.service.RecommendationEngineService;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.risk.repository.RiskAssessmentRepository;
import com.aipclm.system.risk.service.RiskEngineService;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.crm.service.CrmService;
import com.aipclm.system.session.repository.FlightSessionRepository;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationOrchestratorService - Atomic Integrity")
class SimulationOrchestratorServiceTest {

    @Mock private SimulationEngineService simulationEngineService;
    @Mock private CognitiveLoadService cognitiveLoadService;
    @Mock private RiskEngineService riskEngineService;
    @Mock private RecommendationEngineService recommendationEngineService;
    @Mock private TelemetryFrameRepository telemetryFrameRepository;
    @Mock private CognitiveStateRepository cognitiveStateRepository;
    @Mock private RiskAssessmentRepository riskAssessmentRepository;
    @Mock private FlightSessionRepository flightSessionRepository;
    @Mock private CrmService crmService;

    @InjectMocks private SimulationOrchestratorService orchestrator;

    private Pilot pilot;
    private FlightSession session;
    private TelemetryFrame frame;
    private CognitiveState cogState;
    private RiskAssessment riskAssessment;

    @BeforeEach
    void setUp() {
        pilot = TestFixtures.pilotNovice();
        session = TestFixtures.runningSession(pilot);
        frame = TestFixtures.cruiseFrame(session, 1);
        cogState = TestFixtures.cognitiveState(frame, 30, 32, 0.85);
        riskAssessment = TestFixtures.riskAssessment(cogState, RiskLevel.LOW, false);
        // Orchestrator now looks up the session first — stub for all tests
        lenient().when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
    }

    private void stubFullPipeline() {
        when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        doNothing().when(simulationEngineService).generateNextFrame(session.getId());
        when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                .thenReturn(Optional.of(frame));
        when(cognitiveLoadService.computeCognitiveLoad(frame.getId())).thenReturn(30.0);
        when(cognitiveStateRepository.findByTelemetryFrameId(frame.getId()))
                .thenReturn(Optional.of(cogState));
        when(riskEngineService.evaluateRisk(cogState.getId())).thenReturn(riskAssessment);

        AIRecommendation rec = AIRecommendation.builder()
                .id(UUID.randomUUID())
                .riskAssessment(riskAssessment)
                .recommendationType(RecommendationType.MONITOR_ONLY)
                .severity(Severity.INFO)
                .message("ok")
                .timestamp(Instant.now())
                .build();
        when(recommendationEngineService.generateRecommendations(riskAssessment.getId()))
                .thenReturn(List.of(rec));
    }

    // ──────────────────────── Atomic Integrity ────────────────────────

    @Nested
    @DisplayName("Atomic Integrity")
    class AtomicIntegrity {

        @Test
        @DisplayName("Full pipeline produces exactly 1 telemetry, 1 cognitive, 1 risk, ≥1 recommendation")
        void fullPipelineProducesAllRecords() {
            stubFullPipeline();

            SimulationOrchestratorService.SimulationStepResult result =
                    orchestrator.runSingleSimulationStep(session.getId());

            verify(simulationEngineService).generateNextFrame(session.getId());
            verify(cognitiveLoadService).computeCognitiveLoad(frame.getId());
            verify(riskEngineService).evaluateRisk(cogState.getId());
            verify(recommendationEngineService).generateRecommendations(riskAssessment.getId());

            assertThat(result.frameNumber()).isEqualTo(1);
            assertThat(result.riskLevel()).isEqualTo("LOW");
            assertThat(result.recommendations()).hasSize(1);
        }

        @Test
        @DisplayName("Returns correct DTO values from pipeline")
        void correctDtoValues() {
            stubFullPipeline();

            SimulationOrchestratorService.SimulationStepResult result =
                    orchestrator.runSingleSimulationStep(session.getId());

            assertThat(result.phase()).isEqualTo("CRUISE");
            assertThat(result.expertLoad()).isEqualTo(30.0);
            assertThat(result.mlPredictedLoad()).isEqualTo(32.0);
            assertThat(result.confidenceScore()).isEqualTo(0.85);
            assertThat(result.swissCheeseTriggered()).isFalse();
        }
    }

    // ──────────────────────── Rollback Scenarios ────────────────────────

    @Nested
    @DisplayName("Rollback Scenarios")
    class RollbackScenarios {

        @Test
        @DisplayName("Simulation engine failure → exception propagates (triggers rollback)")
        void simulationFailure() {
            doThrow(new RuntimeException("DB error")).when(simulationEngineService)
                    .generateNextFrame(session.getId());

            assertThatThrownBy(() -> orchestrator.runSingleSimulationStep(session.getId()))
                    .isInstanceOf(RuntimeException.class);

            verifyNoInteractions(cognitiveLoadService);
            verifyNoInteractions(riskEngineService);
            verifyNoInteractions(recommendationEngineService);
        }

        @Test
        @DisplayName("CognitiveLoad failure → exception propagates (triggers rollback)")
        void cognitiveLoadFailure() {
            doNothing().when(simulationEngineService).generateNextFrame(session.getId());
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(frame));
            when(cognitiveLoadService.computeCognitiveLoad(frame.getId()))
                    .thenThrow(new RuntimeException("ML failure mid-compute"));

            assertThatThrownBy(() -> orchestrator.runSingleSimulationStep(session.getId()))
                    .isInstanceOf(RuntimeException.class);

            verifyNoInteractions(riskEngineService);
            verifyNoInteractions(recommendationEngineService);
        }

        @Test
        @DisplayName("Risk engine failure → exception propagates (triggers rollback)")
        void riskEngineFailure() {
            doNothing().when(simulationEngineService).generateNextFrame(session.getId());
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(frame));
            when(cognitiveLoadService.computeCognitiveLoad(frame.getId())).thenReturn(30.0);
            when(cognitiveStateRepository.findByTelemetryFrameId(frame.getId()))
                    .thenReturn(Optional.of(cogState));
            when(riskEngineService.evaluateRisk(cogState.getId()))
                    .thenThrow(new RuntimeException("Risk computation error"));

            assertThatThrownBy(() -> orchestrator.runSingleSimulationStep(session.getId()))
                    .isInstanceOf(RuntimeException.class);

            verifyNoInteractions(recommendationEngineService);
        }

        @Test
        @DisplayName("Recommendation failure → exception propagates (triggers rollback)")
        void recommendationFailure() {
            doNothing().when(simulationEngineService).generateNextFrame(session.getId());
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(frame));
            when(cognitiveLoadService.computeCognitiveLoad(frame.getId())).thenReturn(30.0);
            when(cognitiveStateRepository.findByTelemetryFrameId(frame.getId()))
                    .thenReturn(Optional.of(cogState));
            when(riskEngineService.evaluateRisk(cogState.getId())).thenReturn(riskAssessment);
            when(recommendationEngineService.generateRecommendations(riskAssessment.getId()))
                    .thenThrow(new RuntimeException("Rec engine error"));

            assertThatThrownBy(() -> orchestrator.runSingleSimulationStep(session.getId()))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("No TelemetryFrame after generation → exception")
        void noFrameAfterGeneration() {
            doNothing().when(simulationEngineService).generateNextFrame(session.getId());
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orchestrator.runSingleSimulationStep(session.getId()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("No CognitiveState after computation → exception")
        void noCognitiveStateAfterCompute() {
            doNothing().when(simulationEngineService).generateNextFrame(session.getId());
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(frame));
            when(cognitiveLoadService.computeCognitiveLoad(frame.getId())).thenReturn(30.0);
            when(cognitiveStateRepository.findByTelemetryFrameId(frame.getId()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orchestrator.runSingleSimulationStep(session.getId()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Null risk assessment → exception")
        void nullRiskAssessment() {
            doNothing().when(simulationEngineService).generateNextFrame(session.getId());
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(frame));
            when(cognitiveLoadService.computeCognitiveLoad(frame.getId())).thenReturn(30.0);
            when(cognitiveStateRepository.findByTelemetryFrameId(frame.getId()))
                    .thenReturn(Optional.of(cogState));
            when(riskEngineService.evaluateRisk(cogState.getId())).thenReturn(null);

            assertThatThrownBy(() -> orchestrator.runSingleSimulationStep(session.getId()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}

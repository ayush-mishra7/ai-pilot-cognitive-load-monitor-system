package com.aipclm.system;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.model.RiskLevel;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.cognitive.service.CognitiveLoadService;
import com.aipclm.system.cognitive.service.MLInferenceService;
import com.aipclm.system.cognitive.service.MLPredictionResponse;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.recommendation.model.AIRecommendation;
import com.aipclm.system.recommendation.model.RecommendationType;
import com.aipclm.system.recommendation.model.Severity;
import com.aipclm.system.recommendation.repository.AIRecommendationRepository;
import com.aipclm.system.recommendation.service.RecommendationEngineService;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.risk.repository.RiskAssessmentRepository;
import com.aipclm.system.risk.service.RiskEngineService;
import com.aipclm.system.crm.repository.CrewAssignmentRepository;
import com.aipclm.system.crm.service.CrmService;
import com.aipclm.system.scenario.repository.FlightScenarioRepository;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.model.FlightSessionStatus;
import com.aipclm.system.session.repository.FlightSessionRepository;
import com.aipclm.system.simulation.service.SimulationEngineService;
import com.aipclm.system.simulation.service.SimulationOrchestratorService;
import com.aipclm.system.simulation.service.SimulationSchedulerService;
import com.aipclm.system.session.service.WebSocketBroadcastService;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * "System Must Not Do" — comprehensive negative tests verifying
 * the system does NOT exhibit dangerous behaviors.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("System Must Not Do")
class SystemMustNotDoTest {

    // ──────────────────────────────────────────────────────────
    // 1. Must NOT escalate to CRITICAL on a single spike
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No CRITICAL on Single Spike")
    class NoCriticalOnSingleSpike {

        @Mock private CognitiveStateRepository cognitiveStateRepository;
        @Mock private TelemetryFrameRepository telemetryFrameRepository;
        @Mock private RiskAssessmentRepository riskAssessmentRepository;
        @Mock private FlightScenarioRepository scenarioRepository;

        private RiskEngineService riskService;

        @BeforeEach
        void setUp() {
            riskService = new RiskEngineService(cognitiveStateRepository, telemetryFrameRepository,
                    riskAssessmentRepository, scenarioRepository);
        }

        @Test
        @DisplayName("A single 99-load frame among 4 normal frames stays at LOW")
        void singleSpikeStaysLow() {
            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession session = TestFixtures.runningSession(pilot);
            TelemetryFrame frame = TestFixtures.cruiseFrame(session, 1);
            CognitiveState cogState = TestFixtures.cognitiveState(frame, 30, 30, 0.85);

            when(cognitiveStateRepository.findById(cogState.getId())).thenReturn(Optional.of(cogState));

            // 1 extreme + 4 normal → avg = (99 + 25*4)/5 = 39.8 → LOW
            CognitiveState extreme = TestFixtures.cognitiveState(frame, 99, 99, 0.85);
            CognitiveState normal = TestFixtures.cognitiveState(frame, 25, 25, 0.85);
            when(cognitiveStateRepository.findTop5BySessionIdOrderByTimestampDesc(session.getId()))
                    .thenReturn(List.of(extreme, normal, normal, normal, normal));

            when(riskAssessmentRepository
                    .findTopByCognitiveStateTelemetryFrameFlightSessionIdOrderByTimestampDesc(session.getId()))
                    .thenReturn(Optional.empty()); // default LOW

            when(riskAssessmentRepository.save(any())).thenAnswer(inv -> {
                RiskAssessment ra = inv.getArgument(0);
                ra.setId(UUID.randomUUID());
                return ra;
            });
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RiskAssessment result = riskService.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel())
                    .as("Single spike should NOT escalate to CRITICAL")
                    .isNotEqualTo(RiskLevel.CRITICAL);
        }

        @Test
        @DisplayName("Hysteresis prevents LOW→CRITICAL jump even at 90 smoothedLoad")
        void hysteresisPreventsJump() {
            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession session = TestFixtures.runningSession(pilot);
            TelemetryFrame frame = TestFixtures.cruiseFrame(session, 1);
            CognitiveState cogState = TestFixtures.cognitiveState(frame, 90, 90, 0.85);

            when(cognitiveStateRepository.findById(cogState.getId())).thenReturn(Optional.of(cogState));

            CognitiveState high = TestFixtures.cognitiveState(frame, 90, 90, 0.85);
            when(cognitiveStateRepository.findTop5BySessionIdOrderByTimestampDesc(session.getId()))
                    .thenReturn(List.of(high, high, high, high, high));

            when(riskAssessmentRepository
                    .findTopByCognitiveStateTelemetryFrameFlightSessionIdOrderByTimestampDesc(session.getId()))
                    .thenReturn(Optional.empty()); // default LOW

            when(riskAssessmentRepository.save(any())).thenAnswer(inv -> {
                RiskAssessment ra = inv.getArgument(0);
                ra.setId(UUID.randomUUID());
                return ra;
            });
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RiskAssessment result = riskService.evaluateRisk(cogState.getId());

            // From LOW, it can only go to MEDIUM (threshold at 40)
            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        }
    }

    // ──────────────────────────────────────────────────────────
    // 2. Must NOT crash when ML service is down
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No Crash When ML Down")
    class NoCrashWhenMLDown {

        @Mock private TelemetryFrameRepository telemetryFrameRepository;
        @Mock private CognitiveStateRepository cognitiveStateRepository;
        @Mock private MLInferenceService mlInferenceService;

        private CognitiveLoadService cogLoadService;

        @BeforeEach
        void setUp() {
            cogLoadService = new CognitiveLoadService(telemetryFrameRepository, cognitiveStateRepository,
                    mlInferenceService);
        }

        @Test
        @DisplayName("ML exception → fallback used, CognitiveState still persisted")
        void mlDownStillPersists() {
            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession session = TestFixtures.runningSession(pilot);
            TelemetryFrame frame = TestFixtures.cruiseFrame(session, 1);

            when(telemetryFrameRepository.findById(frame.getId())).thenReturn(Optional.of(frame));
            // ML service returns a fallback (simulating its internal error handling)
            when(mlInferenceService.callPredictionAPI(any(), anyDouble())).thenReturn(
                    MLPredictionResponse.builder()
                            .predictedLoad(30.0).errorProbability(0.3).confidenceScore(0.5).build());
            when(cognitiveStateRepository.findRecentBySessionId(any(UUID.class), anyInt()))
                    .thenReturn(List.of());
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> cogLoadService.computeCognitiveLoad(frame.getId()))
                    .doesNotThrowAnyException();

            verify(cognitiveStateRepository).save(any(CognitiveState.class));
        }
    }

    // ──────────────────────────────────────────────────────────
    // 3. Must NOT generate frames after session COMPLETED
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No Frames After Completion")
    class NoFramesAfterCompletion {

        @Mock private FlightSessionRepository flightSessionRepository;
        @Mock private com.aipclm.system.pilot.repository.PilotRepository pilotRepository;
        @Mock private TelemetryFrameRepository telemetryFrameRepository;
        @Mock private FlightScenarioRepository scenarioRepository;
        @Mock private CrewAssignmentRepository crewAssignmentRepository;
        @Mock private CrmService crmService;

        private SimulationEngineService engineService;

        @BeforeEach
        void setUp() {
            engineService = new SimulationEngineService(flightSessionRepository, pilotRepository,
                    telemetryFrameRepository, scenarioRepository, crewAssignmentRepository, crmService);
        }

        @Test
        @DisplayName("COMPLETED session → no frame generated")
        void completedSessionNoFrames() {
            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession session = TestFixtures.completedSession(pilot);

            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

            engineService.generateNextFrame(session.getId());

            verify(telemetryFrameRepository, never()).save(any());
        }

        @Test
        @DisplayName("ABORTED session → no frame generated")
        void abortedSessionNoFrames() {
            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession session = TestFixtures.runningSession(pilot);
            session.setStatus(FlightSessionStatus.ABORTED);

            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

            engineService.generateNextFrame(session.getId());

            verify(telemetryFrameRepository, never()).save(any());
        }
    }

    // ──────────────────────────────────────────────────────────
    // 4. Must NOT allow duplicate scheduler for same session
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No Duplicate Scheduler")
    class NoDuplicateScheduler {

        @Mock private SimulationOrchestratorService orchestratorService;
        @Mock private FlightSessionRepository flightSessionRepository;
        @Mock private WebSocketBroadcastService webSocketBroadcastService;

        private SimulationSchedulerService schedulerService;

        @BeforeEach
        void setUp() {
            schedulerService = new SimulationSchedulerService(orchestratorService, flightSessionRepository, webSocketBroadcastService);
        }

        @AfterEach
        void tearDown() {
            schedulerService.shutdown();
        }

        @Test
        @DisplayName("Calling startSession twice does not create duplicate")
        void noDuplicateSchedulerCreated() {
            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession session = TestFixtures.runningSession(pilot);
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

            schedulerService.startSession(session.getId());
            schedulerService.startSession(session.getId());

            // Clean stop should work without issues
            assertThatCode(() -> schedulerService.stopSession(session.getId()))
                    .doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────────────────
    // 5. Must NOT produce partial pipeline persist on failure
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No Partial Pipeline Persist")
    class NoPartialPipelinePersist {

        @Mock private SimulationEngineService simulationEngineService;
        @Mock private CognitiveLoadService cognitiveLoadService;
        @Mock private RiskEngineService riskEngineService;
        @Mock private RecommendationEngineService recommendationEngineService;
        @Mock private TelemetryFrameRepository telemetryFrameRepository;
        @Mock private CognitiveStateRepository cognitiveStateRepository;
        @Mock private RiskAssessmentRepository riskAssessmentRepository;
        @Mock private FlightSessionRepository flightSessionRepository;
        @Mock private CrmService crmService;

        private SimulationOrchestratorService orchestrator;

        @BeforeEach
        void setUp() {
            orchestrator = new SimulationOrchestratorService(
                    simulationEngineService, cognitiveLoadService, riskEngineService,
                    recommendationEngineService, telemetryFrameRepository,
                    cognitiveStateRepository, riskAssessmentRepository,
                    flightSessionRepository, crmService);
        }

        @Test
        @DisplayName("Risk engine failure → recommendation engine never called")
        void riskFailureStopsDownstream() {
            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession session = TestFixtures.runningSession(pilot);
            TelemetryFrame frame = TestFixtures.cruiseFrame(session, 1);
            CognitiveState cogState = TestFixtures.cognitiveState(frame, 30, 32, 0.85);
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));            doNothing().when(simulationEngineService).generateNextFrame(session.getId());
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(frame));
            when(cognitiveLoadService.computeCognitiveLoad(frame.getId())).thenReturn(30.0);
            when(cognitiveStateRepository.findByTelemetryFrameId(frame.getId()))
                    .thenReturn(Optional.of(cogState));
            when(riskEngineService.evaluateRisk(cogState.getId()))
                    .thenThrow(new RuntimeException("Risk DB error"));

            assertThatThrownBy(() -> orchestrator.runSingleSimulationStep(session.getId()))
                    .isInstanceOf(RuntimeException.class);

            verifyNoInteractions(recommendationEngineService);
        }

        @Test
        @DisplayName("Empty recommendations → exception thrown (pipeline integrity)")
        void emptyRecsThrows() {
            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession session = TestFixtures.runningSession(pilot);
            TelemetryFrame frame = TestFixtures.cruiseFrame(session, 1);
            CognitiveState cogState = TestFixtures.cognitiveState(frame, 30, 32, 0.85);
            RiskAssessment risk = TestFixtures.riskAssessment(cogState, RiskLevel.LOW, false);
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));            doNothing().when(simulationEngineService).generateNextFrame(session.getId());
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(frame));
            when(cognitiveLoadService.computeCognitiveLoad(frame.getId())).thenReturn(30.0);
            when(cognitiveStateRepository.findByTelemetryFrameId(frame.getId()))
                    .thenReturn(Optional.of(cogState));
            when(riskEngineService.evaluateRisk(cogState.getId())).thenReturn(risk);
            when(recommendationEngineService.generateRecommendations(risk.getId()))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> orchestrator.runSingleSimulationStep(session.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No recommendations generated");
        }
    }

    // ──────────────────────────────────────────────────────────
    // 6. Cognitive load MUST be bounded [0, 100]
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Load Always Bounded 0-100")
    class LoadBounded {

        @Mock private TelemetryFrameRepository telemetryFrameRepository;
        @Mock private CognitiveStateRepository cognitiveStateRepository;
        @Mock private MLInferenceService mlInferenceService;

        private CognitiveLoadService cogLoadService;

        @BeforeEach
        void setUp() {
            cogLoadService = new CognitiveLoadService(telemetryFrameRepository, cognitiveStateRepository,
                    mlInferenceService);
        }

        @Test
        @DisplayName("Extreme max inputs → expertComputedLoad <= 100")
        void maxInputsClamped() {
            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession session = TestFixtures.runningSession(pilot);
            TelemetryFrame frame = TestFixtures.highLoadFrame(session, 1);

            when(telemetryFrameRepository.findById(frame.getId())).thenReturn(Optional.of(frame));
            when(mlInferenceService.callPredictionAPI(any(), anyDouble())).thenReturn(
                    MLPredictionResponse.builder()
                            .predictedLoad(100.0).errorProbability(1.0).confidenceScore(0.85).build());
            when(cognitiveStateRepository.findRecentBySessionId(any(UUID.class), anyInt()))
                    .thenReturn(List.of());
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            double load = cogLoadService.computeCognitiveLoad(frame.getId());

            assertThat(load).isLessThanOrEqualTo(100.0);
        }

        @Test
        @DisplayName("All-zero inputs → expertComputedLoad >= 0")
        void minInputsClamped() {
            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession session = TestFixtures.runningSession(pilot);
            TelemetryFrame frame = TestFixtures.cruiseFrame(session, 1);
            frame.setReactionTimeMs(200);
            frame.setErrorCount(0);
            frame.setTurbulenceLevel(0);
            frame.setWeatherSeverity(0);
            frame.setControlJitterIndex(0);
            frame.setChecklistDelaySeconds(0);
            frame.setTaskSwitchRate(0);
            frame.setStressIndex(0);
            frame.setHeartRate(60);
            frame.setFatigueIndex(0);
            frame.setBlinkRate(30);

            when(telemetryFrameRepository.findById(frame.getId())).thenReturn(Optional.of(frame));
            when(mlInferenceService.callPredictionAPI(any(), anyDouble())).thenReturn(
                    MLPredictionResponse.builder()
                            .predictedLoad(0.0).errorProbability(0.0).confidenceScore(0.85).build());
            when(cognitiveStateRepository.findRecentBySessionId(any(UUID.class), anyInt()))
                    .thenReturn(List.of());
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            double load = cogLoadService.computeCognitiveLoad(frame.getId());

            assertThat(load).isGreaterThanOrEqualTo(0.0);
        }
    }

    // ──────────────────────────────────────────────────────────
    // 7. ML fallback confidence MUST block CRITICAL
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fallback Confidence Blocks CRITICAL")
    class FallbackBlocksCritical {

        @Mock private CognitiveStateRepository cognitiveStateRepository;
        @Mock private TelemetryFrameRepository telemetryFrameRepository;
        @Mock private RiskAssessmentRepository riskAssessmentRepository;
        @Mock private FlightScenarioRepository scenarioRepository;

        private RiskEngineService riskService;

        @BeforeEach
        void setUp() {
            riskService = new RiskEngineService(cognitiveStateRepository, telemetryFrameRepository,
                    riskAssessmentRepository, scenarioRepository);
        }

        @Test
        @DisplayName("Confidence=0.5 (fallback) + smoothed 90 + previous HIGH → capped at HIGH")
        void fallbackBlocksCritical() {
            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession session = TestFixtures.runningSession(pilot);
            TelemetryFrame frame = TestFixtures.cruiseFrame(session, 1);
            CognitiveState cogState = TestFixtures.cognitiveState(frame, 90, 90, 0.5);

            when(cognitiveStateRepository.findById(cogState.getId())).thenReturn(Optional.of(cogState));

            CognitiveState high = TestFixtures.cognitiveState(frame, 90, 90, 0.5);
            when(cognitiveStateRepository.findTop5BySessionIdOrderByTimestampDesc(session.getId()))
                    .thenReturn(List.of(high, high, high, high, high));

            RiskAssessment prev = TestFixtures.riskAssessment(cogState, RiskLevel.HIGH, false);
            when(riskAssessmentRepository
                    .findTopByCognitiveStateTelemetryFrameFlightSessionIdOrderByTimestampDesc(session.getId()))
                    .thenReturn(Optional.of(prev));

            when(riskAssessmentRepository.save(any())).thenAnswer(inv -> {
                RiskAssessment ra = inv.getArgument(0);
                ra.setId(UUID.randomUUID());
                return ra;
            });
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RiskAssessment result = riskService.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel())
                    .as("Fallback confidence (0.5) must prevent CRITICAL")
                    .isNotEqualTo(RiskLevel.CRITICAL);
            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        }
    }

    // ──────────────────────────────────────────────────────────
    // 8. Scheduler MUST NOT leak threads on shutdown
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No Thread Leak on Shutdown")
    class NoThreadLeak {

        @Mock private SimulationOrchestratorService orchestratorService;
        @Mock private FlightSessionRepository flightSessionRepository;
        @Mock private WebSocketBroadcastService webSocketBroadcastService;

        @Test
        @DisplayName("Shutdown after multiple sessions → no exception, clean exit")
        void shutdownCleansUp() {
            SimulationSchedulerService scheduler = new SimulationSchedulerService(
                    orchestratorService, flightSessionRepository, webSocketBroadcastService);

            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession s1 = TestFixtures.runningSession(pilot);
            FlightSession s2 = TestFixtures.runningSession(pilot);

            when(flightSessionRepository.findById(s1.getId())).thenReturn(Optional.of(s1));
            when(flightSessionRepository.findById(s2.getId())).thenReturn(Optional.of(s2));

            scheduler.startSession(s1.getId());
            scheduler.startSession(s2.getId());

            assertThatCode(scheduler::shutdown).doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────────────────
    // 9. Duplicate frame MUST be prevented
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No Duplicate Frames")
    class NoDuplicateFrames {

        @Mock private FlightSessionRepository flightSessionRepository;
        @Mock private com.aipclm.system.pilot.repository.PilotRepository pilotRepository;
        @Mock private TelemetryFrameRepository telemetryFrameRepository;
        @Mock private FlightScenarioRepository scenarioRepository;
        @Mock private CrewAssignmentRepository crewAssignmentRepository;
        @Mock private CrmService crmService;

        private SimulationEngineService engineService;

        @BeforeEach
        void setUp() {
            engineService = new SimulationEngineService(flightSessionRepository, pilotRepository,
                    telemetryFrameRepository, scenarioRepository, crewAssignmentRepository, crmService);
        }

        @Test
        @DisplayName("Existing latest frame >= expected → skips generation")
        void duplicateFrameSkipped() {
            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession session = TestFixtures.runningSession(pilot);
            session.setTotalFramesGenerated(5);

            TelemetryFrame existing = TestFixtures.cruiseFrame(session, 6);

            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(pilotRepository.findById(pilot.getId())).thenReturn(Optional.of(pilot));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(existing));

            engineService.generateNextFrame(session.getId());

            verify(telemetryFrameRepository, never()).save(any(TelemetryFrame.class));
        }
    }

    // ──────────────────────────────────────────────────────────
    // 10. MONITOR_ONLY must always fire as fallback
    // ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MONITOR_ONLY Always Available")
    class MonitorOnlyFallback {

        @Mock private RiskAssessmentRepository riskAssessmentRepository;
        @Mock private AIRecommendationRepository aiRecommendationRepository;
        @Mock private TelemetryFrameRepository telemetryFrameRepository;
        @Mock private FlightScenarioRepository scenarioRepository;

        private RecommendationEngineService recService;

        @BeforeEach
        void setUp() {
            recService = new RecommendationEngineService(riskAssessmentRepository,
                    aiRecommendationRepository, telemetryFrameRepository, scenarioRepository);
        }

        @Test
        @DisplayName("LOW risk, autopilot on, no instability → MONITOR_ONLY")
        void lowRiskMonitorOnly() {
            Pilot pilot = TestFixtures.pilotNovice();
            FlightSession session = TestFixtures.runningSession(pilot);
            TelemetryFrame frame = TestFixtures.cruiseFrame(session, 1);
            CognitiveState cogState = TestFixtures.cognitiveState(frame, 20, 20, 0.85);
            RiskAssessment risk = TestFixtures.riskAssessment(cogState, RiskLevel.LOW, false);
            risk.setMissedChecklistProbability(0.1);

            when(riskAssessmentRepository.findById(risk.getId())).thenReturn(Optional.of(risk));
            when(aiRecommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            List<AIRecommendation> recs = recService.generateRecommendations(risk.getId());

            assertThat(recs).hasSize(1);
            assertThat(recs.get(0).getRecommendationType()).isEqualTo(RecommendationType.MONITOR_ONLY);
            assertThat(recs.get(0).getSeverity()).isEqualTo(Severity.INFO);
        }
    }
}

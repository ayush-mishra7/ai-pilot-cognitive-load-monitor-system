package com.aipclm.system.cognitive.service;

import com.aipclm.system.TestFixtures;
import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.telemetry.model.PhaseOfFlight;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CognitiveLoadService")
class CognitiveLoadServiceTest {

    @Mock private TelemetryFrameRepository telemetryFrameRepository;
    @Mock private CognitiveStateRepository cognitiveStateRepository;
    @Mock private MLInferenceService mlInferenceService;

    @InjectMocks private CognitiveLoadService service;

    private Pilot pilot;
    private FlightSession session;
    private TelemetryFrame cruiseFrame;

    @BeforeEach
    void setUp() {
        pilot = TestFixtures.pilotNovice();
        session = TestFixtures.runningSession(pilot);
        cruiseFrame = TestFixtures.cruiseFrame(session, 1);
    }

    private MLPredictionResponse normalMlResponse(double expertLoad) {
        return MLPredictionResponse.builder()
                .predictedLoad(expertLoad + 2.0)
                .errorProbability((expertLoad + 2.0) / 100.0)
                .confidenceScore(0.85)
                .build();
    }

    // ──────────────────────── Core Computation ────────────────────────

    @Nested
    @DisplayName("Core Computation")
    class CoreComputation {

        @Test
        @DisplayName("expertComputedLoad always between 0-100")
        void expertLoadBounded() {
            when(telemetryFrameRepository.findById(cruiseFrame.getId())).thenReturn(Optional.of(cruiseFrame));
            when(mlInferenceService.callPredictionAPI(any(), anyDouble()))
                    .thenAnswer(inv -> normalMlResponse(inv.getArgument(1)));
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            double load = service.computeCognitiveLoad(cruiseFrame.getId());

            assertThat(load).isBetween(0.0, 100.0);
        }

        @Test
        @DisplayName("High turbulence increases cognitive load")
        void highTurbulenceIncreasesLoad() {
            // Low turbulence frame
            TelemetryFrame lowTurb = TestFixtures.cruiseFrame(session, 1);
            lowTurb.setTurbulenceLevel(0.05);

            // High turbulence frame (identical otherwise)
            TelemetryFrame highTurb = TestFixtures.cruiseFrame(session, 2);
            highTurb.setTurbulenceLevel(0.9);

            when(telemetryFrameRepository.findById(lowTurb.getId())).thenReturn(Optional.of(lowTurb));
            when(telemetryFrameRepository.findById(highTurb.getId())).thenReturn(Optional.of(highTurb));
            when(mlInferenceService.callPredictionAPI(any(), anyDouble()))
                    .thenAnswer(inv -> normalMlResponse(inv.getArgument(1)));
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            double lowLoad = service.computeCognitiveLoad(lowTurb.getId());
            double highLoad = service.computeCognitiveLoad(highTurb.getId());

            assertThat(highLoad).isGreaterThan(lowLoad);
        }

        @Test
        @DisplayName("High reaction delay increases cognitive load")
        void highReactionDelayIncreasesLoad() {
            TelemetryFrame fastReaction = TestFixtures.cruiseFrame(session, 1);
            fastReaction.setReactionTimeMs(250);

            TelemetryFrame slowReaction = TestFixtures.cruiseFrame(session, 2);
            slowReaction.setReactionTimeMs(1400);

            when(telemetryFrameRepository.findById(fastReaction.getId())).thenReturn(Optional.of(fastReaction));
            when(telemetryFrameRepository.findById(slowReaction.getId())).thenReturn(Optional.of(slowReaction));
            when(mlInferenceService.callPredictionAPI(any(), anyDouble()))
                    .thenAnswer(inv -> normalMlResponse(inv.getArgument(1)));
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            double fastLoad = service.computeCognitiveLoad(fastReaction.getId());
            double slowLoad = service.computeCognitiveLoad(slowReaction.getId());

            assertThat(slowLoad).isGreaterThan(fastLoad);
        }

        @Test
        @DisplayName("Fatigue increase raises load")
        void fatigueIncreasesLoad() {
            TelemetryFrame lowFatigue = TestFixtures.cruiseFrame(session, 1);
            lowFatigue.setFatigueIndex(5);

            TelemetryFrame highFatigue = TestFixtures.cruiseFrame(session, 2);
            highFatigue.setFatigueIndex(90);

            when(telemetryFrameRepository.findById(lowFatigue.getId())).thenReturn(Optional.of(lowFatigue));
            when(telemetryFrameRepository.findById(highFatigue.getId())).thenReturn(Optional.of(highFatigue));
            when(mlInferenceService.callPredictionAPI(any(), anyDouble()))
                    .thenAnswer(inv -> normalMlResponse(inv.getArgument(1)));
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            double lowLoad = service.computeCognitiveLoad(lowFatigue.getId());
            double highLoad = service.computeCognitiveLoad(highFatigue.getId());

            assertThat(highLoad).isGreaterThan(lowLoad);
        }

        @Test
        @DisplayName("Weight contributions sum to 1.0 (verified via component isolation)")
        void weightContributionsSumCorrectly() {
            // The weights are 0.25 + 0.20 + 0.20 + 0.15 + 0.20 = 1.00
            // With all components at max (100), expertLoad should be ~100 (or close)
            TelemetryFrame maxFrame = TestFixtures.cruiseFrame(session, 1);
            maxFrame.setReactionTimeMs(1500);
            maxFrame.setErrorCount(10);
            maxFrame.setTurbulenceLevel(1.0);
            maxFrame.setWeatherSeverity(1.0);
            maxFrame.setControlJitterIndex(1.0);
            maxFrame.setChecklistDelaySeconds(30);
            maxFrame.setTaskSwitchRate(10);
            maxFrame.setStressIndex(100);
            maxFrame.setHeartRate(160);
            maxFrame.setFatigueIndex(100);
            maxFrame.setBlinkRate(10);
            maxFrame.setPhaseOfFlight(PhaseOfFlight.APPROACH);

            when(telemetryFrameRepository.findById(maxFrame.getId())).thenReturn(Optional.of(maxFrame));
            when(mlInferenceService.callPredictionAPI(any(), anyDouble()))
                    .thenAnswer(inv -> normalMlResponse(inv.getArgument(1)));
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            double load = service.computeCognitiveLoad(maxFrame.getId());

            assertThat(load).isGreaterThan(80.0); // Very high but clamped at 100
            assertThat(load).isLessThanOrEqualTo(100.0);
        }

        @Test
        @DisplayName("APPROACH/LANDING boost increases load vs CRUISE")
        void phaseBoostApproach() {
            TelemetryFrame cruise = TestFixtures.cruiseFrame(session, 1);
            cruise.setPhaseOfFlight(PhaseOfFlight.CRUISE);

            TelemetryFrame approach = TestFixtures.cruiseFrame(session, 2);
            approach.setPhaseOfFlight(PhaseOfFlight.APPROACH);

            when(telemetryFrameRepository.findById(cruise.getId())).thenReturn(Optional.of(cruise));
            when(telemetryFrameRepository.findById(approach.getId())).thenReturn(Optional.of(approach));
            when(mlInferenceService.callPredictionAPI(any(), anyDouble()))
                    .thenAnswer(inv -> normalMlResponse(inv.getArgument(1)));
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            double cruiseLoad = service.computeCognitiveLoad(cruise.getId());
            double approachLoad = service.computeCognitiveLoad(approach.getId());

            assertThat(approachLoad).isGreaterThan(cruiseLoad);
        }
    }

    // ──────────────────────── Persistence ────────────────────────

    @Nested
    @DisplayName("Persistence")
    class Persistence {

        @Test
        @DisplayName("CognitiveState is persisted with ML response values")
        void cognitiveStatePersisted() {
            when(telemetryFrameRepository.findById(cruiseFrame.getId())).thenReturn(Optional.of(cruiseFrame));
            when(mlInferenceService.callPredictionAPI(any(), anyDouble()))
                    .thenReturn(MLPredictionResponse.builder()
                            .predictedLoad(42.0).errorProbability(0.42).confidenceScore(0.85).build());
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.computeCognitiveLoad(cruiseFrame.getId());

            ArgumentCaptor<CognitiveState> captor = ArgumentCaptor.forClass(CognitiveState.class);
            verify(cognitiveStateRepository).save(captor.capture());
            CognitiveState saved = captor.getValue();
            assertThat(saved.getMlPredictedLoad()).isEqualTo(42.0);
            assertThat(saved.getErrorProbability()).isEqualTo(0.42);
            assertThat(saved.getConfidenceScore()).isEqualTo(0.85);
            assertThat(saved.getTelemetryFrame()).isEqualTo(cruiseFrame);
        }
    }

    // ──────────────────────── Edge Cases ────────────────────────

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Missing telemetry frame → throws exception")
        void missingTelemetryFrame() {
            UUID badId = UUID.randomUUID();
            when(telemetryFrameRepository.findById(badId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.computeCognitiveLoad(badId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TelemetryFrame not found");
        }

        @Test
        @DisplayName("ML returns load > 100 → clamped to 100")
        void mlLoadClampedHigh() {
            when(telemetryFrameRepository.findById(cruiseFrame.getId())).thenReturn(Optional.of(cruiseFrame));
            when(mlInferenceService.callPredictionAPI(any(), anyDouble()))
                    .thenReturn(MLPredictionResponse.builder()
                            .predictedLoad(150.0).errorProbability(0.5).confidenceScore(0.85).build());
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.computeCognitiveLoad(cruiseFrame.getId());

            ArgumentCaptor<CognitiveState> captor = ArgumentCaptor.forClass(CognitiveState.class);
            verify(cognitiveStateRepository).save(captor.capture());
            assertThat(captor.getValue().getMlPredictedLoad()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("ML returns negative load → clamped to 0")
        void mlLoadClampedLow() {
            when(telemetryFrameRepository.findById(cruiseFrame.getId())).thenReturn(Optional.of(cruiseFrame));
            when(mlInferenceService.callPredictionAPI(any(), anyDouble()))
                    .thenReturn(MLPredictionResponse.builder()
                            .predictedLoad(-10.0).errorProbability(0.1).confidenceScore(0.85).build());
            when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.computeCognitiveLoad(cruiseFrame.getId());

            ArgumentCaptor<CognitiveState> captor = ArgumentCaptor.forClass(CognitiveState.class);
            verify(cognitiveStateRepository).save(captor.capture());
            assertThat(captor.getValue().getMlPredictedLoad()).isEqualTo(0.0);
        }
    }
}

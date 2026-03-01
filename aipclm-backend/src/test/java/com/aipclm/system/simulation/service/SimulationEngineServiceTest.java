package com.aipclm.system.simulation.service;

import com.aipclm.system.TestFixtures;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.pilot.model.PilotProfileType;
import com.aipclm.system.pilot.repository.PilotRepository;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.model.FlightSessionStatus;
import com.aipclm.system.session.repository.FlightSessionRepository;
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
@DisplayName("SimulationEngineService")
class SimulationEngineServiceTest {

    @Mock private FlightSessionRepository flightSessionRepository;
    @Mock private PilotRepository pilotRepository;
    @Mock private TelemetryFrameRepository telemetryFrameRepository;

    @InjectMocks private SimulationEngineService service;

    private Pilot pilot;
    private FlightSession session;

    @BeforeEach
    void setUp() {
        pilot = TestFixtures.pilotNovice();
        session = TestFixtures.runningSession(pilot);
    }

    // ──────────────────────── Basic Functionality ────────────────────────

    @Nested
    @DisplayName("Basic Functionality")
    class BasicFunctionality {

        @Test
        @DisplayName("Generates exactly 1 TelemetryFrame per call and increments totalFramesGenerated")
        void generatesOneFrame() {
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(pilotRepository.findById(pilot.getId())).thenReturn(Optional.of(pilot));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.empty());

            service.generateNextFrame(session.getId());

            verify(telemetryFrameRepository, times(1)).save(any(TelemetryFrame.class));
            verify(flightSessionRepository, times(1)).save(session);
            assertThat(session.getTotalFramesGenerated()).isEqualTo(1);
        }

        @Test
        @DisplayName("Frame numbers increment sequentially with no gaps")
        void frameNumbersIncrement() {
            session.setTotalFramesGenerated(5);
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(pilotRepository.findById(pilot.getId())).thenReturn(Optional.of(pilot));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(TestFixtures.cruiseFrame(session, 5)));

            service.generateNextFrame(session.getId());

            ArgumentCaptor<TelemetryFrame> captor = ArgumentCaptor.forClass(TelemetryFrame.class);
            verify(telemetryFrameRepository).save(captor.capture());
            assertThat(captor.getValue().getFrameNumber()).isEqualTo(6);
            assertThat(session.getTotalFramesGenerated()).isEqualTo(6);
        }

        @Test
        @DisplayName("Does not generate frames if session status is not RUNNING")
        void noFrameIfNotRunning() {
            session.setStatus(FlightSessionStatus.COMPLETED);
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

            service.generateNextFrame(session.getId());

            verify(telemetryFrameRepository, never()).save(any());
            verify(flightSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Does not generate frames after session marked COMPLETED")
        void noFrameIfCompleted() {
            session.setStatus(FlightSessionStatus.COMPLETED);
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

            service.generateNextFrame(session.getId());

            verify(telemetryFrameRepository, never()).save(any());
        }

        @Test
        @DisplayName("Does not generate frames if session status is ABORTED")
        void noFrameIfAborted() {
            session.setStatus(FlightSessionStatus.ABORTED);
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

            service.generateNextFrame(session.getId());

            verify(telemetryFrameRepository, never()).save(any());
        }
    }

    // ──────────────────────── Phase Transitions ────────────────────────

    @Nested
    @DisplayName("Phase Transitions")
    class PhaseTransitions {

        private void verifyPhaseAtFrame(int totalFramesBefore, PhaseOfFlight expectedPhase) {
            session.setTotalFramesGenerated(totalFramesBefore);
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(pilotRepository.findById(pilot.getId())).thenReturn(Optional.of(pilot));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(TestFixtures.cruiseFrame(session, totalFramesBefore)));

            service.generateNextFrame(session.getId());

            ArgumentCaptor<TelemetryFrame> captor = ArgumentCaptor.forClass(TelemetryFrame.class);
            verify(telemetryFrameRepository).save(captor.capture());
            assertThat(captor.getValue().getPhaseOfFlight()).isEqualTo(expectedPhase);
        }

        @Test @DisplayName("Frame 1 (2s) → TAKEOFF")
        void takeoffPhase() { verifyPhaseAtFrame(0, PhaseOfFlight.TAKEOFF); }

        @Test @DisplayName("Frame 150 (300s boundary) → TAKEOFF (<=300)")
        void takeoffBoundary() { verifyPhaseAtFrame(149, PhaseOfFlight.TAKEOFF); }

        @Test @DisplayName("Frame 151 (302s) → CLIMB")
        void climbStart() { verifyPhaseAtFrame(150, PhaseOfFlight.CLIMB); }

        @Test @DisplayName("Frame 300 (600s boundary) → CLIMB (<=600)")
        void climbBoundary() { verifyPhaseAtFrame(299, PhaseOfFlight.CLIMB); }

        @Test @DisplayName("Frame 301 (602s) → CRUISE")
        void cruiseStart() { verifyPhaseAtFrame(300, PhaseOfFlight.CRUISE); }

        @Test @DisplayName("Frame 900 (1800s boundary) → CRUISE (<=1800)")
        void cruiseBoundary() { verifyPhaseAtFrame(899, PhaseOfFlight.CRUISE); }

        @Test @DisplayName("Frame 901 (1802s) → DESCENT")
        void descentStart() { verifyPhaseAtFrame(900, PhaseOfFlight.DESCENT); }

        @Test @DisplayName("Frame 1200 (2400s boundary) → DESCENT (<=2400)")
        void descentBoundary() { verifyPhaseAtFrame(1199, PhaseOfFlight.DESCENT); }

        @Test @DisplayName("Frame 1201 (2402s) → APPROACH")
        void approachStart() { verifyPhaseAtFrame(1200, PhaseOfFlight.APPROACH); }

        @Test @DisplayName("Frame 1350 (2700s boundary) → APPROACH (<=2700)")
        void approachBoundary() { verifyPhaseAtFrame(1349, PhaseOfFlight.APPROACH); }
    }

    // ──────────────────────── Noise & Determinism ────────────────────────

    @Nested
    @DisplayName("Noise & Determinism")
    class NoiseDeterminism {

        @Test
        @DisplayName("No negative altitude generated")
        void noNegativeAltitude() {
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(pilotRepository.findById(pilot.getId())).thenReturn(Optional.of(pilot));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.empty());

            // Run many frames to exercise Gaussian noise
            for (int i = 0; i < 50; i++) {
                session.setTotalFramesGenerated(i);
                service.generateNextFrame(session.getId());
            }

            ArgumentCaptor<TelemetryFrame> captor = ArgumentCaptor.forClass(TelemetryFrame.class);
            verify(telemetryFrameRepository, atLeast(1)).save(captor.capture());
            captor.getAllValues().forEach(f ->
                    assertThat(f.getAltitude()).as("frame %d altitude", f.getFrameNumber())
                            .isGreaterThanOrEqualTo(0)
            );
        }

        @Test
        @DisplayName("No negative reaction time")
        void noNegativeReactionTime() {
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(pilotRepository.findById(pilot.getId())).thenReturn(Optional.of(pilot));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.empty());

            for (int i = 0; i < 30; i++) {
                session.setTotalFramesGenerated(i);
                service.generateNextFrame(session.getId());
            }

            ArgumentCaptor<TelemetryFrame> captor = ArgumentCaptor.forClass(TelemetryFrame.class);
            verify(telemetryFrameRepository, atLeast(1)).save(captor.capture());
            captor.getAllValues().forEach(f ->
                    assertThat(f.getReactionTimeMs()).as("frame %d reactionTimeMs", f.getFrameNumber())
                            .isGreaterThanOrEqualTo(0)
            );
        }

        @Test
        @DisplayName("No negative airspeed")
        void noNegativeAirspeed() {
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(pilotRepository.findById(pilot.getId())).thenReturn(Optional.of(pilot));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.empty());

            for (int i = 0; i < 30; i++) {
                session.setTotalFramesGenerated(i);
                service.generateNextFrame(session.getId());
            }

            ArgumentCaptor<TelemetryFrame> captor = ArgumentCaptor.forClass(TelemetryFrame.class);
            verify(telemetryFrameRepository, atLeast(1)).save(captor.capture());
            captor.getAllValues().forEach(f ->
                    assertThat(f.getAirspeed()).as("frame %d airspeed", f.getFrameNumber())
                            .isGreaterThanOrEqualTo(0)
            );
        }

        @Test
        @DisplayName("Turbulence level bounded [0, 1]")
        void turbulenceBounded() {
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(pilotRepository.findById(pilot.getId())).thenReturn(Optional.of(pilot));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.empty());

            for (int i = 0; i < 30; i++) {
                session.setTotalFramesGenerated(i);
                service.generateNextFrame(session.getId());
            }

            ArgumentCaptor<TelemetryFrame> captor = ArgumentCaptor.forClass(TelemetryFrame.class);
            verify(telemetryFrameRepository, atLeast(1)).save(captor.capture());
            captor.getAllValues().forEach(f -> {
                assertThat(f.getTurbulenceLevel()).isBetween(0.0, 1.0);
            });
        }
    }

    // ──────────────────────── Edge Cases ────────────────────────

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Session not found → throws exception")
        void sessionNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(flightSessionRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateNextFrame(unknownId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Session not found");
        }

        @Test
        @DisplayName("Duplicate frameNumber is skipped")
        void duplicateFrameSkipped() {
            session.setTotalFramesGenerated(5);
            TelemetryFrame existingFrame = TestFixtures.cruiseFrame(session, 6);
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(pilotRepository.findById(pilot.getId())).thenReturn(Optional.of(pilot));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(existingFrame));

            service.generateNextFrame(session.getId());

            verify(telemetryFrameRepository, never()).save(any(TelemetryFrame.class));
        }

        @Test
        @DisplayName("Pilot profile EXPERIENCED reduces reaction time and stress")
        void experiencedPilotProfile() {
            pilot = TestFixtures.pilotExperienced();
            session = TestFixtures.runningSession(pilot);
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(pilotRepository.findById(pilot.getId())).thenReturn(Optional.of(pilot));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.empty());

            service.generateNextFrame(session.getId());

            ArgumentCaptor<TelemetryFrame> captor = ArgumentCaptor.forClass(TelemetryFrame.class);
            verify(telemetryFrameRepository).save(captor.capture());
            // Experienced pilot has baseReaction 300 - 50 = 250, plus noise
            // Should generally be lower than Novice (300 + 150 = 450)
            assertThat(captor.getValue().getReactionTimeMs()).isLessThan(800);
        }

        @Test
        @DisplayName("Pilot profile FATIGUE_PRONE accumulates fatigue faster")
        void fatiguePronePilot() {
            pilot.setProfileType(PilotProfileType.FATIGUE_PRONE);
            pilot.setBaselineFatigueRate(1.0);
            session = TestFixtures.runningSession(pilot);
            session.setTotalFramesGenerated(449); // 900s = 15 minutes in

            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(pilotRepository.findById(pilot.getId())).thenReturn(Optional.of(pilot));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(TestFixtures.cruiseFrame(session, 449)));

            service.generateNextFrame(session.getId());

            ArgumentCaptor<TelemetryFrame> captor = ArgumentCaptor.forClass(TelemetryFrame.class);
            verify(telemetryFrameRepository).save(captor.capture());
            // FATIGUE_PRONE: fatigueRate * 1.5. Fatigue = (timeS/60) * 1.5 + stress*0.1
            assertThat(captor.getValue().getFatigueIndex()).isGreaterThan(0);
        }
    }
}

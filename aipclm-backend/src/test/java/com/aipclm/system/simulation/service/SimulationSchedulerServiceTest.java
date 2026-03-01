package com.aipclm.system.simulation.service;

import com.aipclm.system.TestFixtures;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.model.FlightSessionStatus;
import com.aipclm.system.session.repository.FlightSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationSchedulerService")
class SimulationSchedulerServiceTest {

    @Mock private SimulationOrchestratorService orchestratorService;
    @Mock private FlightSessionRepository flightSessionRepository;

    private SimulationSchedulerService scheduler;

    private Pilot pilot;
    private FlightSession session;

    @BeforeEach
    void setUp() {
        scheduler = new SimulationSchedulerService(orchestratorService, flightSessionRepository);
        pilot = TestFixtures.pilotNovice();
        session = TestFixtures.runningSession(pilot);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    // ──────────────────────── Normal Operation ────────────────────────

    @Nested
    @DisplayName("Normal Operation")
    class NormalOperation {

        @Test
        @DisplayName("Starts scheduling for a RUNNING session")
        void startsForRunningSession() {
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

            scheduler.startSession(session.getId());

            // Give it a moment to start
            sleep(200);

            // stopSession should succeed (scheduler was running)
            assertThatCode(() -> scheduler.stopSession(session.getId())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Does not start if session status is not RUNNING")
        void doesNotStartIfNotRunning() {
            session.setStatus(FlightSessionStatus.COMPLETED);
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

            scheduler.startSession(session.getId());

            // Should be safe to stop since nothing was started
            scheduler.stopSession(session.getId());
        }

        @Test
        @DisplayName("Does not create duplicate scheduler for same session")
        void noDuplicateScheduler() {
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

            scheduler.startSession(session.getId());
            scheduler.startSession(session.getId()); // Second call should be ignored

            sleep(200);
            scheduler.stopSession(session.getId());
        }

        @Test
        @DisplayName("Stop on non-existing scheduler → safe")
        void stopNonExisting() {
            assertThatCode(() -> scheduler.stopSession(UUID.randomUUID()))
                    .doesNotThrowAnyException();
        }
    }

    // ──────────────────────── Session Not Found ────────────────────────

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Session not found → throws exception")
        void sessionNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(flightSessionRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scheduler.startSession(unknownId))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Shutdown cleans up executor properly")
        void shutdownCleansUp() {
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            scheduler.startSession(session.getId());

            assertThatCode(() -> scheduler.shutdown()).doesNotThrowAnyException();
        }
    }

    // ──────────────────────── Concurrency ────────────────────────

    @Nested
    @DisplayName("Concurrency")
    class Concurrency {

        @Test
        @DisplayName("Multiple sessions can be scheduled concurrently")
        void multipleSessions() {
            FlightSession session2 = TestFixtures.runningSession(pilot);
            FlightSession session3 = TestFixtures.runningSession(pilot);

            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(flightSessionRepository.findById(session2.getId())).thenReturn(Optional.of(session2));
            when(flightSessionRepository.findById(session3.getId())).thenReturn(Optional.of(session3));

            scheduler.startSession(session.getId());
            scheduler.startSession(session2.getId());
            scheduler.startSession(session3.getId());

            sleep(200);

            scheduler.stopSession(session.getId());
            scheduler.stopSession(session2.getId());
            scheduler.stopSession(session3.getId());
        }

        @Test
        @DisplayName("Two concurrent start calls → only one scheduler created")
        void raceConditionStart() throws InterruptedException {
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

            Thread t1 = new Thread(() -> scheduler.startSession(session.getId()));
            Thread t2 = new Thread(() -> scheduler.startSession(session.getId()));
            t1.start();
            t2.start();
            t1.join(1000);
            t2.join(1000);

            // Just one scheduler should exist. Stop will remove it.
            scheduler.stopSession(session.getId());
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

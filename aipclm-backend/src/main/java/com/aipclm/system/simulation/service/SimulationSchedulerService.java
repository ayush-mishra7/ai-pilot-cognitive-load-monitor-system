package com.aipclm.system.simulation.service;

import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.model.FlightSessionStatus;
import com.aipclm.system.session.repository.FlightSessionRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SimulationSchedulerService {

    private final SimulationOrchestratorService orchestratorService;
    private final FlightSessionRepository flightSessionRepository;

    private final ScheduledExecutorService executorService;
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> activeTasks;

    public SimulationSchedulerService(SimulationOrchestratorService orchestratorService,
            FlightSessionRepository flightSessionRepository) {
        this.orchestratorService = orchestratorService;
        this.flightSessionRepository = flightSessionRepository;
        this.executorService = Executors.newScheduledThreadPool(10);
        this.activeTasks = new ConcurrentHashMap<>();
    }

    /**
     * Starts a scheduled simulation for the given session ID.
     * Prevents duplicate scheduling.
     * Automatically stops if the session reaches 1350 frames or its status changes.
     */
    public synchronized void startSession(UUID sessionId) {
        if (activeTasks.containsKey(sessionId)) {
            log.warn("[Scheduler] Session {} is already running a simulation loop.", sessionId);
            return;
        }

        FlightSession session = flightSessionRepository.findById(sessionId).orElseThrow(
                () -> new IllegalStateException("Session not found: " + sessionId));

        if (session.getStatus() != FlightSessionStatus.RUNNING) {
            log.error("[Scheduler] Cannot start session {}. Status is {}.", sessionId, session.getStatus());
            return;
        }

        long frameFrequencySeconds = session.getFrameFrequencySeconds() > 0 ? session.getFrameFrequencySeconds() : 2L;
        log.info("[Scheduler] Starting simulation loop for session {} at rate {} seconds.", sessionId,
                frameFrequencySeconds);

        ScheduledFuture<?> future = executorService.scheduleAtFixedRate(() -> {
            try {
                // Fetch fresh session state to check stop conditions reliably
                FlightSession currentSession = flightSessionRepository.findById(sessionId).orElse(null);

                if (currentSession == null) {
                    log.warn("[Scheduler] Session {} disappeared. Stopping loop.", sessionId);
                    stopSession(sessionId);
                    return;
                }

                if (currentSession.getStatus() == FlightSessionStatus.COMPLETED
                        || currentSession.getStatus() == FlightSessionStatus.ABORTED) {
                    log.info("[Scheduler] Session {} status is {}. Stopping loop.", sessionId,
                            currentSession.getStatus());
                    stopSession(sessionId);
                    return;
                }

                if (currentSession.getTotalFramesGenerated() >= 1350) {
                    log.info("[Scheduler] Session {} reached max frames (1350). Stopping loop.", sessionId);
                    stopSession(sessionId);

                    // Mark session as completed
                    currentSession.setStatus(FlightSessionStatus.COMPLETED);
                    flightSessionRepository.save(currentSession);
                    return;
                }

                // Execute the single block orchestrated simulation step
                orchestratorService.runSingleSimulationStep(sessionId);

            } catch (Exception e) {
                log.error("[Scheduler] Error running simulation step for session {}: {}", sessionId, e.getMessage(), e);
                // Do NOT stop the session on transient error unless deemed critical
            }
        }, 0, frameFrequencySeconds, TimeUnit.SECONDS);

        activeTasks.put(sessionId, future);
    }

    /**
     * Cancels the scheduled task for this session safely.
     */
    public synchronized void stopSession(UUID sessionId) {
        ScheduledFuture<?> future = activeTasks.remove(sessionId);
        if (future != null) {
            future.cancel(true); // Interrupt running threads safely to force shutdown
            log.info("[Scheduler] Stopped simulation loop for session {}.", sessionId);
        } else {
            log.warn("[Scheduler] Session {} was not actively running.", sessionId);
        }
    }

    /**
     * Gracefully shuts down the executor pool when Spring context is destroyed (app
     * shutdown).
     */
    @PreDestroy
    public void shutdown() {
        log.info("[Scheduler] Shutting down SimulationSchedulerService executor pool.");
        activeTasks.values().forEach(future -> future.cancel(false));
        activeTasks.clear();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

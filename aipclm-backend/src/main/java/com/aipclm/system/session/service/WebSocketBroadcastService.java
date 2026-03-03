package com.aipclm.system.session.service;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.recommendation.model.AIRecommendation;
import com.aipclm.system.recommendation.repository.AIRecommendationRepository;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.risk.repository.RiskAssessmentRepository;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.repository.FlightSessionRepository;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Broadcasts simulation data over STOMP WebSocket topics after each pipeline step.
 *
 * <p>This service is called by the {@code SimulationSchedulerService} right after the
 * orchestrator transaction commits. It reads the freshly persisted data and publishes
 * it to the appropriate WebSocket topics so every connected dashboard gets a real-time
 * push instead of having to poll.</p>
 *
 * <h3>Topics published</h3>
 * <ul>
 *   <li>{@code /topic/session/{id}/state}            — full latest state snapshot</li>
 *   <li>{@code /topic/session/{id}/cognitive-history} — single new cognitive entry</li>
 *   <li>{@code /topic/session/{id}/risk-history}      — single new risk entry</li>
 *   <li>{@code /topic/sessions}                       — session list update (for home & radar)</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final FlightSessionRepository flightSessionRepository;
    private final TelemetryFrameRepository telemetryFrameRepository;
    private final CognitiveStateRepository cognitiveStateRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final AIRecommendationRepository recommendationRepository;

    /**
     * Broadcast latest state for a session after a pipeline step completes.
     * Called outside the @Transactional boundary so reads committed data.
     */
    public void broadcastSessionState(UUID sessionId) {
        try {
            FlightSession session = flightSessionRepository.findById(sessionId).orElse(null);
            if (session == null) return;

            TelemetryFrame frame = telemetryFrameRepository
                    .findTopByFlightSessionIdOrderByFrameNumberDesc(sessionId).orElse(null);
            if (frame == null) return;

            CognitiveState cogState = cognitiveStateRepository
                    .findByTelemetryFrameId(frame.getId()).orElse(null);
            RiskAssessment risk = null;
            List<AIRecommendation> recs = List.of();

            if (cogState != null) {
                risk = riskAssessmentRepository.findByCognitiveStateId(cogState.getId()).orElse(null);
                if (risk != null) {
                    recs = recommendationRepository.findByRiskAssessmentId(risk.getId());
                }
            }

            // 1) Full state snapshot → /topic/session/{id}/state
            SessionStateMessage stateMsg = buildStateMessage(sessionId, frame, cogState, risk, recs);
            messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/state", stateMsg);

            // 2) Cognitive history entry → /topic/session/{id}/cognitive-history
            if (cogState != null) {
                CognitiveHistoryEntry cogEntry = buildCognitiveEntry(cogState, risk);
                messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/cognitive-history", cogEntry);
            }

            // 3) Risk history entry → /topic/session/{id}/risk-history
            if (risk != null) {
                RiskHistoryEntry riskEntry = buildRiskEntry(risk);
                messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/risk-history", riskEntry);
            }

            // 4) Session list update → /topic/sessions
            broadcastSessionList();

            log.debug("[WebSocket] Broadcast complete for session={} frame={}", sessionId, frame.getFrameNumber());

        } catch (Exception e) {
            log.warn("[WebSocket] Broadcast failed for session={}: {}", sessionId, e.getMessage());
            // Non-critical — don't propagate. REST polling is still available as fallback.
        }
    }

    /**
     * Broadcast session list update (for HomePage and AtcRadarPage).
     * Called after session creation, stop, or completion.
     */
    public void broadcastSessionList() {
        try {
            List<FlightSession> sessions = flightSessionRepository.findAll();
            List<SessionSummaryMessage> summaries = sessions.stream()
                    .map(s -> SessionSummaryMessage.builder()
                            .id(s.getId())
                            .status(s.getStatus().name())
                            .pilotName(s.getPilot() != null ? s.getPilot().getFullName() : "Unknown")
                            .totalFrames(s.getTotalFramesGenerated())
                            .createdAt(s.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());
            messagingTemplate.convertAndSend("/topic/sessions", summaries);
        } catch (Exception e) {
            log.warn("[WebSocket] Session list broadcast failed: {}", e.getMessage());
        }
    }

    // ───────────────────── DTO Builders ─────────────────────

    private SessionStateMessage buildStateMessage(UUID sessionId, TelemetryFrame frame,
                                                   CognitiveState cogState, RiskAssessment risk,
                                                   List<AIRecommendation> recs) {
        return SessionStateMessage.builder()
                .sessionId(sessionId)
                .frameNumber(frame.getFrameNumber())
                .timestamp(frame.getTimestamp())
                .telemetry(TelemetryMessage.builder()
                        .phaseOfFlight(frame.getPhaseOfFlight().name())
                        .altitude(frame.getAltitude())
                        .airspeed(frame.getAirspeed())
                        .turbulenceLevel(frame.getTurbulenceLevel())
                        .heartRate(frame.getHeartRate())
                        .stressIndex(frame.getStressIndex())
                        .fatigueIndex(frame.getFatigueIndex())
                        .build())
                .cognitiveState(cogState != null ? CognitiveStateMessage.builder()
                        .expertComputedLoad(cogState.getExpertComputedLoad())
                        .mlPredictedLoad(cogState.getMlPredictedLoad())
                        .smoothedLoad(cogState.getSmoothedLoad())
                        .confidenceScore(cogState.getConfidenceScore())
                        .errorProbability(cogState.getErrorProbability())
                        .fatigueTrendSlope(cogState.getFatigueTrendSlope())
                        .swissCheeseAlignmentScore(cogState.getSwissCheeseAlignmentScore())
                        .riskLevel(risk != null ? risk.getRiskLevel().name() : "UNKNOWN")
                        .build() : null)
                .recommendations(recs.stream()
                        .map(r -> RecommendationMessage.builder()
                                .recommendationType(r.getRecommendationType().name())
                                .message(r.getMessage())
                                .severity(r.getSeverity().name())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private CognitiveHistoryEntry buildCognitiveEntry(CognitiveState s, RiskAssessment risk) {
        return CognitiveHistoryEntry.builder()
                .expertComputedLoad(s.getExpertComputedLoad())
                .mlPredictedLoad(s.getMlPredictedLoad())
                .smoothedLoad(s.getSmoothedLoad())
                .confidenceScore(s.getConfidenceScore())
                .errorProbability(s.getErrorProbability())
                .fatigueTrendSlope(s.getFatigueTrendSlope())
                .swissCheeseAlignmentScore(s.getSwissCheeseAlignmentScore())
                .riskLevel(risk != null ? risk.getRiskLevel().name() : "UNKNOWN")
                .build();
    }

    private RiskHistoryEntry buildRiskEntry(RiskAssessment r) {
        return RiskHistoryEntry.builder()
                .riskLevel(r.getRiskLevel().name())
                .aggregatedRiskScore(r.getAggregatedRiskScore())
                .riskEscalated(r.isRiskEscalated())
                .swissCheeseTriggered(r.isSwissCheeseTriggered())
                .timestamp(r.getTimestamp())
                .build();
    }

    // ───────────────────── Message DTOs ─────────────────────

    @Data @Builder
    public static class SessionStateMessage {
        private UUID sessionId;
        private int frameNumber;
        private Instant timestamp;
        private TelemetryMessage telemetry;
        private CognitiveStateMessage cognitiveState;
        private List<RecommendationMessage> recommendations;
    }

    @Data @Builder
    public static class TelemetryMessage {
        private String phaseOfFlight;
        private double altitude;
        private double airspeed;
        private double turbulenceLevel;
        private double heartRate;
        private double stressIndex;
        private double fatigueIndex;
    }

    @Data @Builder
    public static class CognitiveStateMessage {
        private double expertComputedLoad;
        private double mlPredictedLoad;
        private double smoothedLoad;
        private double confidenceScore;
        private double errorProbability;
        private double fatigueTrendSlope;
        private double swissCheeseAlignmentScore;
        private String riskLevel;
    }

    @Data @Builder
    public static class RecommendationMessage {
        private String recommendationType;
        private String message;
        private String severity;
    }

    @Data @Builder
    public static class CognitiveHistoryEntry {
        private double expertComputedLoad;
        private double mlPredictedLoad;
        private double smoothedLoad;
        private double confidenceScore;
        private double errorProbability;
        private double fatigueTrendSlope;
        private double swissCheeseAlignmentScore;
        private String riskLevel;
    }

    @Data @Builder
    public static class RiskHistoryEntry {
        private String riskLevel;
        private double aggregatedRiskScore;
        private boolean riskEscalated;
        private boolean swissCheeseTriggered;
        private Instant timestamp;
    }

    @Data @Builder
    public static class SessionSummaryMessage {
        private UUID id;
        private String status;
        private String pilotName;
        private int totalFrames;
        private Instant createdAt;
    }
}

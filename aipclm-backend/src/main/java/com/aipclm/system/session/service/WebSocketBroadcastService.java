package com.aipclm.system.session.service;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.crm.model.CrmAssessment;
import com.aipclm.system.crm.repository.CrmAssessmentRepository;
import com.aipclm.system.pilot.model.CrewRole;
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
    private final CrmAssessmentRepository crmAssessmentRepository;

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

            // Attach crew data if crew mode
            if (session.isCrewMode()) {
                attachCrewData(stateMsg, sessionId, frame.getFrameNumber());
            }

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

            // 4) CRM history entry → /topic/session/{id}/crm-history (crew mode only)
            if (session.isCrewMode()) {
                CrmAssessment crm = crmAssessmentRepository
                        .findTopByFlightSessionIdOrderByFrameNumberDesc(sessionId).orElse(null);
                if (crm != null) {
                    CrmHistoryEntry crmEntry = CrmHistoryEntry.builder()
                            .communicationScore(crm.getCommunicationScore())
                            .workloadDistribution(crm.getWorkloadDistribution())
                            .authorityGradient(crm.getAuthorityGradient())
                            .situationalAwareness(crm.getSituationalAwarenessScore())
                            .crmEffectiveness(crm.getCrmEffectivenessScore())
                            .fatigueSymmetry(crm.getFatigueSymmetry())
                            .captainLoad(crm.getCaptainLoad())
                            .firstOfficerLoad(crm.getFirstOfficerLoad())
                            .build();
                    messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/crm-history", crmEntry);
                }
            }

            // 5) Session list update → /topic/sessions
            broadcastSessionList();

            log.debug("[WebSocket] Broadcast complete for session={} frame={}", sessionId, frame.getFrameNumber());

        } catch (Exception e) {
            log.warn("[WebSocket] Broadcast failed for session={}: {}", sessionId, e.getMessage());
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
                            .crewMode(s.isCrewMode())
                            .sensorMode(s.isSensorMode())
                            .icaoAirport(s.getIcaoAirport())
                            .adsbMode(s.isAdsbMode())
                            .build())
                    .collect(Collectors.toList());
            messagingTemplate.convertAndSend("/topic/sessions", summaries);
        } catch (Exception e) {
            log.warn("[WebSocket] Session list broadcast failed: {}", e.getMessage());
        }
    }

    // ───────────────────── DTO Builders ─────────────────────

    private TelemetryMessage buildTelemetryMessage(TelemetryFrame frame) {
        return TelemetryMessage.builder()
                .phaseOfFlight(frame.getPhaseOfFlight().name())
                .altitude(frame.getAltitude())
                .airspeed(frame.getAirspeed())
                .turbulenceLevel(frame.getTurbulenceLevel())
                .heartRate(frame.getHeartRate())
                .stressIndex(frame.getStressIndex())
                .fatigueIndex(frame.getFatigueIndex())
                .gsrLevel(frame.getGsrLevel())
                .spO2Level(frame.getSpO2Level())
                .skinTemperature(frame.getSkinTemperature())
                .eegAlphaPower(frame.getEegAlphaPower())
                .eegBetaPower(frame.getEegBetaPower())
                .eegThetaPower(frame.getEegThetaPower())
                .pupilDiameter(frame.getPupilDiameter())
                .gazeFixationDurationMs(frame.getGazeFixationDurationMs())
                .sensorOverride(frame.isSensorOverride())
                // Phase 8: Weather & ADS-B
                .weatherSeverity(frame.getWeatherSeverity())
                .windShearIndex(frame.getWindShearIndex())
                .icingLevel(frame.getIcingLevel())
                .ceilingFt(frame.getCeilingFt())
                .visibilityNm(frame.getVisibilityNm())
                .nearbyAircraftCount(frame.getNearbyAircraftCount())
                .closestAircraftDistanceNm(frame.getClosestAircraftDistanceNm())
                .tcasAdvisoryActive(frame.isTcasAdvisoryActive())
                .build();
    }

    /**
     * Attaches crew-specific data to the state message for crew-mode sessions.
     */
    private void attachCrewData(SessionStateMessage stateMsg, UUID sessionId, int frameNumber) {
        try {
            // Get FO frame for same frame number
            var frames = telemetryFrameRepository.findByFlightSessionIdAndFrameNumber(sessionId, frameNumber);
            TelemetryFrame foFrame = frames.stream()
                    .filter(f -> f.getCrewRole() == CrewRole.FIRST_OFFICER).findFirst().orElse(null);
            TelemetryFrame captainFrame = frames.stream()
                    .filter(f -> f.getCrewRole() == CrewRole.CAPTAIN).findFirst().orElse(null);

            if (captainFrame != null && foFrame != null) {
                CognitiveState captainCog = cognitiveStateRepository
                        .findByTelemetryFrameId(captainFrame.getId()).orElse(null);
                CognitiveState foCog = cognitiveStateRepository
                        .findByTelemetryFrameId(foFrame.getId()).orElse(null);

                stateMsg.setCrewMode(true);
                stateMsg.setCaptainTelemetry(buildTelemetryMessage(captainFrame));
                stateMsg.setFoTelemetry(buildTelemetryMessage(foFrame));

                if (captainCog != null) {
                    stateMsg.setCaptainCognitive(CognitiveStateMessage.builder()
                            .expertComputedLoad(captainCog.getExpertComputedLoad())
                            .mlPredictedLoad(captainCog.getMlPredictedLoad())
                            .smoothedLoad(captainCog.getSmoothedLoad())
                            .confidenceScore(captainCog.getConfidenceScore())
                            .errorProbability(captainCog.getErrorProbability())
                            .fatigueTrendSlope(captainCog.getFatigueTrendSlope())
                            .swissCheeseAlignmentScore(captainCog.getSwissCheeseAlignmentScore())
                            .riskLevel("N/A")
                            .build());
                }
                if (foCog != null) {
                    stateMsg.setFoCognitive(CognitiveStateMessage.builder()
                            .expertComputedLoad(foCog.getExpertComputedLoad())
                            .mlPredictedLoad(foCog.getMlPredictedLoad())
                            .smoothedLoad(foCog.getSmoothedLoad())
                            .confidenceScore(foCog.getConfidenceScore())
                            .errorProbability(foCog.getErrorProbability())
                            .fatigueTrendSlope(foCog.getFatigueTrendSlope())
                            .swissCheeseAlignmentScore(foCog.getSwissCheeseAlignmentScore())
                            .riskLevel("N/A")
                            .build());
                }
            }

            // CRM assessment
            CrmAssessment crm = crmAssessmentRepository
                    .findTopByFlightSessionIdOrderByFrameNumberDesc(sessionId).orElse(null);
            if (crm != null) {
                stateMsg.setCrmData(CrmDataMessage.builder()
                        .communicationScore(crm.getCommunicationScore())
                        .workloadDistribution(crm.getWorkloadDistribution())
                        .authorityGradient(crm.getAuthorityGradient())
                        .situationalAwareness(crm.getSituationalAwarenessScore())
                        .crmEffectiveness(crm.getCrmEffectivenessScore())
                        .fatigueSymmetry(crm.getFatigueSymmetry())
                        .captainLoad(crm.getCaptainLoad())
                        .firstOfficerLoad(crm.getFirstOfficerLoad())
                        .build());
            }
        } catch (Exception e) {
            log.warn("[WebSocket] Failed to attach crew data for session={}: {}", sessionId, e.getMessage());
        }
    }

    private SessionStateMessage buildStateMessage(UUID sessionId, TelemetryFrame frame,
                                                   CognitiveState cogState, RiskAssessment risk,
                                                   List<AIRecommendation> recs) {
        return SessionStateMessage.builder()
                .sessionId(sessionId)
                .frameNumber(frame.getFrameNumber())
                .timestamp(frame.getTimestamp())
                .telemetry(buildTelemetryMessage(frame))
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
        // Crew mode fields (null for single-pilot sessions)
        @Builder.Default private boolean crewMode = false;
        private TelemetryMessage captainTelemetry;
        private TelemetryMessage foTelemetry;
        private CognitiveStateMessage captainCognitive;
        private CognitiveStateMessage foCognitive;
        private CrmDataMessage crmData;
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
        // Sensor biometrics (null when no sensor connected)
        private Double gsrLevel;
        private Double spO2Level;
        private Double skinTemperature;
        private Double eegAlphaPower;
        private Double eegBetaPower;
        private Double eegThetaPower;
        private Double pupilDiameter;
        private Double gazeFixationDurationMs;
        @Builder.Default private boolean sensorOverride = false;
        // Phase 8: Weather & ADS-B
        private double weatherSeverity;
        private Double windShearIndex;
        private Double icingLevel;
        private Double ceilingFt;
        private Double visibilityNm;
        private Integer nearbyAircraftCount;
        private Double closestAircraftDistanceNm;
        @Builder.Default private boolean tcasAdvisoryActive = false;
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
        @Builder.Default private boolean crewMode = false;
        @Builder.Default private boolean sensorMode = false;
        // Phase 8
        private String icaoAirport;
        @Builder.Default private boolean adsbMode = false;
    }

    @Data @Builder
    public static class CrmDataMessage {
        private double communicationScore;
        private double workloadDistribution;
        private double authorityGradient;
        private double situationalAwareness;
        private double crmEffectiveness;
        private double fatigueSymmetry;
        private double captainLoad;
        private double firstOfficerLoad;
    }

    @Data @Builder
    public static class CrmHistoryEntry {
        private double communicationScore;
        private double workloadDistribution;
        private double authorityGradient;
        private double situationalAwareness;
        private double crmEffectiveness;
        private double fatigueSymmetry;
        private double captainLoad;
        private double firstOfficerLoad;
    }
}

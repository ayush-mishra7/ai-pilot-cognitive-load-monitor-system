package com.aipclm.system.session.controller;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.cognitive.service.MLExplainResponse;
import com.aipclm.system.cognitive.service.MLInferenceService;
import com.aipclm.system.crm.model.CrmAssessment;
import com.aipclm.system.crm.repository.CrmAssessmentRepository;
import com.aipclm.system.recommendation.model.AIRecommendation;
import com.aipclm.system.recommendation.repository.AIRecommendationRepository;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.risk.repository.RiskAssessmentRepository;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.repository.FlightSessionRepository;
import com.aipclm.system.session.service.WebSocketBroadcastService;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import jakarta.persistence.EntityManager;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionMonitoringController {

    private final FlightSessionRepository flightSessionRepository;
    private final TelemetryFrameRepository telemetryFrameRepository;
    private final CognitiveStateRepository cognitiveStateRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final AIRecommendationRepository recommendationRepository;
    private final EntityManager entityManager;
    private final WebSocketBroadcastService webSocketBroadcastService;
    private final MLInferenceService mlInferenceService;
    private final CrmAssessmentRepository crmAssessmentRepository;

    /* ─── Health check ─── */
    @GetMapping("/health")
    public ResponseEntity<java.util.Map<String, String>> health() {
        return ResponseEntity.ok(java.util.Map.of("status", "UP"));
    }

    /* ─── Purge ALL sessions and related data ─── */
    @DeleteMapping("/purge-all")
    @Transactional
    public ResponseEntity<java.util.Map<String, Object>> purgeAllSessions() {
        long deleted = flightSessionRepository.count();
        entityManager.createNativeQuery("DELETE FROM ai_recommendation").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM risk_assessment").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM cognitive_state").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM sensor_reading").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM sensor_device").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM telemetry_frame").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM crm_assessment").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM crew_assignments").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM flight_scenario").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM weather_observation").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM adsb_aircraft").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM flight_sessions").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM pilots").executeUpdate();
        webSocketBroadcastService.broadcastSessionList();
        return ResponseEntity.ok(java.util.Map.of("purged", deleted));
    }

    /* ─── Delete a session and all related data ─── */
    @DeleteMapping("/{sessionId}")
    @Transactional
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
        FlightSession session = flightSessionRepository.findById(sessionId).orElse(null);
        if (session == null) return ResponseEntity.notFound().build();

        UUID pilotId = session.getPilot().getId();

        // Delete in FK dependency order using native SQL
        entityManager.createNativeQuery(
            "DELETE FROM ai_recommendation WHERE risk_assessment_id IN " +
            "(SELECT ra.id FROM risk_assessment ra " +
            "JOIN cognitive_state cs ON ra.cognitive_state_id = cs.id " +
            "JOIN telemetry_frame tf ON cs.telemetry_frame_id = tf.id " +
            "WHERE tf.flight_session_id = :sid)")
            .setParameter("sid", sessionId).executeUpdate();

        entityManager.createNativeQuery(
            "DELETE FROM risk_assessment WHERE cognitive_state_id IN " +
            "(SELECT cs.id FROM cognitive_state cs " +
            "JOIN telemetry_frame tf ON cs.telemetry_frame_id = tf.id " +
            "WHERE tf.flight_session_id = :sid)")
            .setParameter("sid", sessionId).executeUpdate();

        entityManager.createNativeQuery(
            "DELETE FROM cognitive_state WHERE telemetry_frame_id IN " +
            "(SELECT tf.id FROM telemetry_frame tf WHERE tf.flight_session_id = :sid)")
            .setParameter("sid", sessionId).executeUpdate();

        entityManager.createNativeQuery(
            "DELETE FROM telemetry_frame WHERE flight_session_id = :sid")
            .setParameter("sid", sessionId).executeUpdate();

        entityManager.createNativeQuery(
            "DELETE FROM sensor_reading WHERE flight_session_id = :sid")
            .setParameter("sid", sessionId).executeUpdate();

        entityManager.createNativeQuery(
            "DELETE FROM sensor_device WHERE flight_session_id = :sid")
            .setParameter("sid", sessionId).executeUpdate();

        entityManager.createNativeQuery(
            "DELETE FROM crm_assessment WHERE flight_session_id = :sid")
            .setParameter("sid", sessionId).executeUpdate();

        entityManager.createNativeQuery(
            "DELETE FROM crew_assignments WHERE flight_session_id = :sid")
            .setParameter("sid", sessionId).executeUpdate();

        entityManager.createNativeQuery(
            "DELETE FROM flight_scenario WHERE flight_session_id = :sid")
            .setParameter("sid", sessionId).executeUpdate();

        flightSessionRepository.deleteById(sessionId);

        // Clean up orphaned pilot
        entityManager.createNativeQuery(
            "DELETE FROM pilots WHERE id = :pid AND id NOT IN " +
            "(SELECT pilot_id FROM flight_sessions)")
            .setParameter("pid", pilotId).executeUpdate();

        webSocketBroadcastService.broadcastSessionList();

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{sessionId}/latest-state")
    public ResponseEntity<SessionStateDto> getLatestSessionState(@PathVariable UUID sessionId) {

        // 1. Validate session
        FlightSession session = flightSessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        // 2. Fetch latest telemetry frame
        TelemetryFrame latestFrame = telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(sessionId)
                .orElse(null);
        if (latestFrame == null) {
            return ResponseEntity.notFound().build(); // Standard if simulation hasn't generated a single step yet
        }

        // 3. Drill down conditionally to avoid lazy loading N+1 pitfalls on the entity
        // layer
        CognitiveState cogState = cognitiveStateRepository.findByTelemetryFrameId(latestFrame.getId()).orElse(null);
        RiskAssessment riskAssessment = null;
        List<AIRecommendation> recommendations = List.of();

        if (cogState != null) {
            riskAssessment = riskAssessmentRepository.findByCognitiveStateId(cogState.getId()).orElse(null);
            if (riskAssessment != null) {
                // Fetch recommendations using a specific query avoiding N+1 collections
                recommendations = recommendationRepository.findByRiskAssessmentId(riskAssessment.getId());
            }
        }

        // 4. Map to clear DTOs safely resolving nulls
        SessionStateDto response = SessionStateDto.builder()
                .sessionId(sessionId)
                .frameNumber(latestFrame.getFrameNumber())
                .timestamp(latestFrame.getTimestamp())
                .telemetry(mapTelemetry(latestFrame))
                .cognitiveState(mapCognitiveState(cogState, riskAssessment))
                .recommendations(recommendations.stream().map(this::mapRecommendation).collect(Collectors.toList()))
                .build();

        return ResponseEntity.ok(response);
    }

    /* ─── List all sessions ─── */
    @GetMapping("/list")
    public ResponseEntity<List<SessionSummaryDto>> listSessions() {
        List<FlightSession> sessions = flightSessionRepository.findAll();
        List<SessionSummaryDto> result = sessions.stream()
                .map(s -> SessionSummaryDto.builder()
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
        return ResponseEntity.ok(result);
    }

    /* ─── Full cognitive history for a session ─── */
    @GetMapping("/{sessionId}/cognitive-history")
    public ResponseEntity<List<CognitiveStateDto>> getCognitiveHistory(@PathVariable UUID sessionId) {
        FlightSession session = flightSessionRepository.findById(sessionId).orElse(null);
        if (session == null) return ResponseEntity.notFound().build();

        List<CognitiveState> states = cognitiveStateRepository.findAllBySessionIdOrderByTimestampAsc(sessionId);
        List<CognitiveStateDto> dtos = states.stream().map(s -> {
            RiskAssessment risk = riskAssessmentRepository.findByCognitiveStateId(s.getId()).orElse(null);
            return CognitiveStateDto.builder()
                    .expertComputedLoad(s.getExpertComputedLoad())
                    .mlPredictedLoad(s.getMlPredictedLoad())
                    .smoothedLoad(s.getSmoothedLoad())
                    .confidenceScore(s.getConfidenceScore())
                    .errorProbability(s.getErrorProbability())
                    .fatigueTrendSlope(s.getFatigueTrendSlope())
                    .swissCheeseAlignmentScore(s.getSwissCheeseAlignmentScore())
                    .riskLevel(risk != null ? risk.getRiskLevel().name() : "UNKNOWN")
                    .build();
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /* ─── Full risk history for a session ─── */
    @GetMapping("/{sessionId}/risk-history")
    public ResponseEntity<List<RiskHistoryDto>> getRiskHistory(@PathVariable UUID sessionId) {
        FlightSession session = flightSessionRepository.findById(sessionId).orElse(null);
        if (session == null) return ResponseEntity.notFound().build();

        List<RiskAssessment> assessments = riskAssessmentRepository
                .findByCognitiveStateTelemetryFrameFlightSessionIdOrderByTimestampAsc(sessionId);
        List<RiskHistoryDto> dtos = assessments.stream()
                .map(r -> RiskHistoryDto.builder()
                        .riskLevel(r.getRiskLevel().name())
                        .aggregatedRiskScore(r.getAggregatedRiskScore())
                        .riskEscalated(r.isRiskEscalated())
                        .swissCheeseTriggered(r.isSwissCheeseTriggered())
                        .timestamp(r.getTimestamp())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /* ─── SHAP Explainability for latest frame ─── */
    @GetMapping("/{sessionId}/explainability")
    public ResponseEntity<ExplainabilityDto> getExplainability(@PathVariable UUID sessionId) {
        FlightSession session = flightSessionRepository.findById(sessionId).orElse(null);
        if (session == null) return ResponseEntity.notFound().build();

        TelemetryFrame latestFrame = telemetryFrameRepository
                .findTopByFlightSessionIdOrderByFrameNumberDesc(sessionId).orElse(null);
        if (latestFrame == null) return ResponseEntity.notFound().build();

        CognitiveState cogState = cognitiveStateRepository
                .findByTelemetryFrameId(latestFrame.getId()).orElse(null);
        if (cogState == null) return ResponseEntity.notFound().build();

        MLExplainResponse explain = mlInferenceService.callExplainAPI(latestFrame, cogState.getExpertComputedLoad());
        if (explain == null) {
            return ResponseEntity.ok(ExplainabilityDto.builder()
                    .available(false)
                    .predictedLoad(cogState.getMlPredictedLoad())
                    .build());
        }

        List<FeatureContributionDto> contributions = explain.getFeatureContributions().stream()
                .map(fc -> FeatureContributionDto.builder()
                        .feature(fc.getFeature())
                        .value(fc.getValue())
                        .shapValue(fc.getShapValue())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ExplainabilityDto.builder()
                .available(true)
                .predictedLoad(explain.getPredictedLoad())
                .baseValue(explain.getBaseValue())
                .featureContributions(contributions)
                .topPositiveDrivers(explain.getTopPositiveDrivers())
                .topNegativeDrivers(explain.getTopNegativeDrivers())
                .build());
    }

    /* ─── CRM history for a crew-mode session ─── */
    @GetMapping("/{sessionId}/crm-history")
    public ResponseEntity<List<CrmHistoryDto>> getCrmHistory(@PathVariable UUID sessionId) {
        FlightSession session = flightSessionRepository.findById(sessionId).orElse(null);
        if (session == null) return ResponseEntity.notFound().build();
        if (!session.isCrewMode()) return ResponseEntity.ok(List.of());

        List<CrmAssessment> assessments = crmAssessmentRepository
                .findByFlightSessionIdOrderByFrameNumberAsc(sessionId);
        List<CrmHistoryDto> dtos = assessments.stream()
                .map(c -> CrmHistoryDto.builder()
                        .communicationScore(c.getCommunicationScore())
                        .workloadDistribution(c.getWorkloadDistribution())
                        .authorityGradient(c.getAuthorityGradient())
                        .situationalAwareness(c.getSituationalAwarenessScore())
                        .crmEffectiveness(c.getCrmEffectivenessScore())
                        .fatigueSymmetry(c.getFatigueSymmetry())
                        .captainLoad(c.getCaptainLoad())
                        .firstOfficerLoad(c.getFirstOfficerLoad())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    private TelemetryDto mapTelemetry(TelemetryFrame frame) {
        if (frame == null)
            return null;
        return TelemetryDto.builder()
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

    private CognitiveStateDto mapCognitiveState(CognitiveState state, RiskAssessment risk) {
        if (state == null)
            return null;
        return CognitiveStateDto.builder()
                .expertComputedLoad(state.getExpertComputedLoad())
                .mlPredictedLoad(state.getMlPredictedLoad())
                .smoothedLoad(state.getSmoothedLoad())
                .confidenceScore(state.getConfidenceScore())
                .errorProbability(state.getErrorProbability())
                .fatigueTrendSlope(state.getFatigueTrendSlope())
                .swissCheeseAlignmentScore(state.getSwissCheeseAlignmentScore())
                .riskLevel(risk != null ? risk.getRiskLevel().name() : "UNKNOWN")
                .build();
    }

    private RecommendationDto mapRecommendation(AIRecommendation rec) {
        if (rec == null)
            return null;
        return RecommendationDto.builder()
                .recommendationType(rec.getRecommendationType().name())
                .message(rec.getMessage())
                .severity(rec.getSeverity().name())
                .build();
    }

    // --- DTO Classes ---

    @Data
    @Builder
    public static class SessionStateDto {
        private UUID sessionId;
        private int frameNumber;
        private Instant timestamp;
        private TelemetryDto telemetry;
        private CognitiveStateDto cognitiveState;
        private List<RecommendationDto> recommendations;
    }

    @Data
    @Builder
    public static class TelemetryDto {
        private String phaseOfFlight;
        private double altitude;
        private double airspeed;
        private double turbulenceLevel;
        private double heartRate;
        private double stressIndex;
        private double fatigueIndex;
        // Sensor biometrics
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

    @Data
    @Builder
    public static class CognitiveStateDto {
        private double expertComputedLoad;
        private double mlPredictedLoad;
        private double smoothedLoad;
        private double confidenceScore;
        private double errorProbability;
        private double fatigueTrendSlope;
        private double swissCheeseAlignmentScore;
        private String riskLevel;
    }

    @Data
    @Builder
    public static class RecommendationDto {
        private String recommendationType;
        private String message;
        private String severity;
    }

    @Data
    @Builder
    public static class SessionSummaryDto {
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

    @Data
    @Builder
    public static class RiskHistoryDto {
        private String riskLevel;
        private double aggregatedRiskScore;
        private boolean riskEscalated;
        private boolean swissCheeseTriggered;
        private Instant timestamp;
    }

    @Data
    @Builder
    public static class ExplainabilityDto {
        private boolean available;
        private double predictedLoad;
        @Builder.Default
        private double baseValue = 0.0;
        @Builder.Default
        private List<FeatureContributionDto> featureContributions = List.of();
        @Builder.Default
        private List<String> topPositiveDrivers = List.of();
        @Builder.Default
        private List<String> topNegativeDrivers = List.of();
    }

    @Data
    @Builder
    public static class FeatureContributionDto {
        private String feature;
        private double value;
        private double shapValue;
    }

    @Data
    @Builder
    public static class CrmHistoryDto {
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

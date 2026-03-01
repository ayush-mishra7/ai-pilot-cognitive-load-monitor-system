package com.aipclm.system.session.controller;

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
import org.springframework.http.ResponseEntity;
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

    private TelemetryDto mapTelemetry(TelemetryFrame frame) {
        if (frame == null)
            return null;
        return TelemetryDto.builder()
                .phaseOfFlight(frame.getPhaseOfFlight().name())
                .altitude(frame.getAltitude())
                .airspeed(frame.getAirspeed())
                .turbulenceLevel(frame.getTurbulenceLevel())
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
    }

    @Data
    @Builder
    public static class CognitiveStateDto {
        private double expertComputedLoad;
        private double mlPredictedLoad;
        private double smoothedLoad;
        private double confidenceScore;
        private double errorProbability;
        private String riskLevel;
    }

    @Data
    @Builder
    public static class RecommendationDto {
        private String recommendationType;
        private String message;
        private String severity;
    }
}

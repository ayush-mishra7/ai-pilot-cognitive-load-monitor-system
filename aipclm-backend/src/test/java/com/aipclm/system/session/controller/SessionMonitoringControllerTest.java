package com.aipclm.system.session.controller;

import com.aipclm.system.TestFixtures;
import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.cognitive.service.MLInferenceService;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.recommendation.model.AIRecommendation;
import com.aipclm.system.recommendation.model.RecommendationType;
import com.aipclm.system.recommendation.model.Severity;
import com.aipclm.system.recommendation.repository.AIRecommendationRepository;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.risk.repository.RiskAssessmentRepository;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.repository.FlightSessionRepository;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.crm.repository.CrmAssessmentRepository;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import com.aipclm.system.cognitive.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionMonitoringController - Security & Safety")
class SessionMonitoringControllerTest {

    @Mock private FlightSessionRepository flightSessionRepository;
    @Mock private TelemetryFrameRepository telemetryFrameRepository;
    @Mock private CognitiveStateRepository cognitiveStateRepository;
    @Mock private RiskAssessmentRepository riskAssessmentRepository;
    @Mock private AIRecommendationRepository recommendationRepository;
    @Mock private MLInferenceService mlInferenceService;
    @Mock private CrmAssessmentRepository crmAssessmentRepository;

    @InjectMocks private SessionMonitoringController controller;

    private Pilot pilot;
    private FlightSession session;
    private TelemetryFrame frame;
    private CognitiveState cogState;
    private RiskAssessment riskAssessment;

    @BeforeEach
    void setUp() {
        pilot = TestFixtures.pilotNovice();
        session = TestFixtures.runningSession(pilot);
        frame = TestFixtures.cruiseFrame(session, 5);
        cogState = TestFixtures.cognitiveState(frame, 30, 32, 0.85);
        riskAssessment = TestFixtures.riskAssessment(cogState, RiskLevel.LOW, false);
    }

    // ──────────────────────── DTO-only Exposure ────────────────────────

    @Nested
    @DisplayName("DTO-Only Exposure (No Raw Entities)")
    class DtoExposure {

        @Test
        @DisplayName("Response body is SessionStateDto, not a JPA entity")
        void responseIsDto() {
            stubFullChain();

            ResponseEntity<SessionMonitoringController.SessionStateDto> response =
                    controller.getLatestSessionState(session.getId());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isInstanceOf(SessionMonitoringController.SessionStateDto.class);
        }

        @Test
        @DisplayName("Telemetry DTO contains only safe fields (no raw entity)")
        void telemetryDtoSafe() {
            stubFullChain();

            ResponseEntity<SessionMonitoringController.SessionStateDto> response =
                    controller.getLatestSessionState(session.getId());

            SessionMonitoringController.SessionStateDto body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getTelemetry()).isNotNull();
            assertThat(body.getTelemetry().getPhaseOfFlight()).isNotNull();
            assertThat(body.getTelemetry().getAltitude()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("CognitiveState DTO exposes riskLevel as String, not enum")
        void riskLevelAsString() {
            stubFullChain();

            ResponseEntity<SessionMonitoringController.SessionStateDto> response =
                    controller.getLatestSessionState(session.getId());

            assertThat(response.getBody().getCognitiveState().getRiskLevel()).isInstanceOf(String.class);
        }

        @Test
        @DisplayName("Recommendations mapped to RecommendationDto list")
        void recommendationsMapped() {
            AIRecommendation rec = AIRecommendation.builder()
                    .id(UUID.randomUUID())
                    .riskAssessment(riskAssessment)
                    .recommendationType(RecommendationType.MONITOR_ONLY)
                    .severity(Severity.INFO)
                    .message("All good.")
                    .timestamp(Instant.now())
                    .build();
            stubFullChainWithRecs(List.of(rec));

            ResponseEntity<SessionMonitoringController.SessionStateDto> response =
                    controller.getLatestSessionState(session.getId());

            assertThat(response.getBody().getRecommendations()).hasSize(1);
            assertThat(response.getBody().getRecommendations().get(0).getRecommendationType()).isEqualTo("MONITOR_ONLY");
            assertThat(response.getBody().getRecommendations().get(0).getSeverity()).isEqualTo("INFO");
        }
    }

    // ──────────────────────── Not Found Handling ────────────────────────

    @Nested
    @DisplayName("Not Found Handling")
    class NotFoundHandling {

        @Test
        @DisplayName("Unknown sessionId → 404")
        void unknownSession404() {
            UUID unknownId = UUID.randomUUID();
            when(flightSessionRepository.findById(unknownId)).thenReturn(Optional.empty());

            ResponseEntity<SessionMonitoringController.SessionStateDto> response =
                    controller.getLatestSessionState(unknownId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("No telemetry frame → 404")
        void noTelemetryFrame404() {
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.empty());

            ResponseEntity<SessionMonitoringController.SessionStateDto> response =
                    controller.getLatestSessionState(session.getId());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("No cognitive state → response still OK with null cognitiveState")
        void noCognitiveState() {
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(frame));
            when(cognitiveStateRepository.findByTelemetryFrameId(frame.getId())).thenReturn(Optional.empty());

            ResponseEntity<SessionMonitoringController.SessionStateDto> response =
                    controller.getLatestSessionState(session.getId());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getCognitiveState()).isNull();
            assertThat(response.getBody().getRecommendations()).isEmpty();
        }
    }

    // ──────────────────────── Null Safety ────────────────────────

    @Nested
    @DisplayName("Null Safety")
    class NullSafety {

        @Test
        @DisplayName("No risk assessment → cognitiveState present but riskLevel=UNKNOWN")
        void noRiskAssessment() {
            when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
            when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                    .thenReturn(Optional.of(frame));
            when(cognitiveStateRepository.findByTelemetryFrameId(frame.getId()))
                    .thenReturn(Optional.of(cogState));
            when(riskAssessmentRepository.findByCognitiveStateId(cogState.getId()))
                    .thenReturn(Optional.empty());

            ResponseEntity<SessionMonitoringController.SessionStateDto> response =
                    controller.getLatestSessionState(session.getId());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getCognitiveState().getRiskLevel()).isEqualTo("UNKNOWN");
            assertThat(response.getBody().getRecommendations()).isEmpty();
        }
    }

    // ──────── Helpers ────────

    private void stubFullChain() {
        stubFullChainWithRecs(List.of());
    }

    private void stubFullChainWithRecs(List<AIRecommendation> recs) {
        when(flightSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(telemetryFrameRepository.findTopByFlightSessionIdOrderByFrameNumberDesc(session.getId()))
                .thenReturn(Optional.of(frame));
        when(cognitiveStateRepository.findByTelemetryFrameId(frame.getId()))
                .thenReturn(Optional.of(cogState));
        when(riskAssessmentRepository.findByCognitiveStateId(cogState.getId()))
                .thenReturn(Optional.of(riskAssessment));
        when(recommendationRepository.findByRiskAssessmentId(riskAssessment.getId()))
                .thenReturn(recs);
    }
}

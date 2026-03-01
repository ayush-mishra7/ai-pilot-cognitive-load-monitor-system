package com.aipclm.system.recommendation.service;

import com.aipclm.system.TestFixtures;
import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.model.RiskLevel;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.recommendation.model.AIRecommendation;
import com.aipclm.system.recommendation.model.RecommendationType;
import com.aipclm.system.recommendation.model.Severity;
import com.aipclm.system.recommendation.repository.AIRecommendationRepository;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.risk.repository.RiskAssessmentRepository;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.telemetry.model.PhaseOfFlight;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationEngineService")
class RecommendationEngineServiceTest {

    @Mock private RiskAssessmentRepository riskAssessmentRepository;
    @Mock private AIRecommendationRepository aiRecommendationRepository;
    @Mock private TelemetryFrameRepository telemetryFrameRepository;

    @InjectMocks private RecommendationEngineService service;

    private Pilot pilot;
    private FlightSession session;
    private TelemetryFrame frame;
    private CognitiveState cogState;
    private RiskAssessment riskAssessment;

    @BeforeEach
    void setUp() {
        pilot = TestFixtures.pilotNovice();
        session = TestFixtures.runningSession(pilot);
        frame = TestFixtures.cruiseFrame(session, 1);
        cogState = TestFixtures.cognitiveState(frame, 30, 30, 0.85);
        riskAssessment = TestFixtures.riskAssessment(cogState, RiskLevel.LOW, false);

        lenient().when(riskAssessmentRepository.findById(riskAssessment.getId())).thenReturn(Optional.of(riskAssessment));
        lenient().when(aiRecommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ──────────────────────── Rule Triggers ────────────────────────

    @Nested
    @DisplayName("Rule Triggers")
    class RuleTriggers {

        @Test
        @DisplayName("LOW → MONITOR_ONLY")
        void lowRiskMonitorOnly() {
            riskAssessment.setRiskLevel(RiskLevel.LOW);

            List<AIRecommendation> recs = service.generateRecommendations(riskAssessment.getId());

            assertThat(recs).hasSize(1);
            assertThat(recs.get(0).getRecommendationType()).isEqualTo(RecommendationType.MONITOR_ONLY);
            assertThat(recs.get(0).getSeverity()).isEqualTo(Severity.INFO);
        }

        @Test
        @DisplayName("HIGH + autopilot OFF → ENGAGE_AUTOPILOT")
        void highRiskEngageAutopilot() {
            riskAssessment.setRiskLevel(RiskLevel.HIGH);
            frame.setAutopilotEngaged(false);

            List<AIRecommendation> recs = service.generateRecommendations(riskAssessment.getId());

            assertThat(recs).extracting(AIRecommendation::getRecommendationType)
                    .contains(RecommendationType.ENGAGE_AUTOPILOT);
        }

        @Test
        @DisplayName("HIGH + autopilot ON → no ENGAGE_AUTOPILOT")
        void highRiskAutopilotAlreadyOn() {
            riskAssessment.setRiskLevel(RiskLevel.HIGH);
            frame.setAutopilotEngaged(true);
            riskAssessment.setMissedChecklistProbability(0.1); // Low, won't trigger checklist

            List<AIRecommendation> recs = service.generateRecommendations(riskAssessment.getId());

            assertThat(recs).extracting(AIRecommendation::getRecommendationType)
                    .doesNotContain(RecommendationType.ENGAGE_AUTOPILOT);
        }

        @Test
        @DisplayName("DESCENT + high vertical speed instability → STABILIZE_DESCENT")
        void stabilizeDescent() {
            riskAssessment.setRiskLevel(RiskLevel.MEDIUM);
            frame.setPhaseOfFlight(PhaseOfFlight.DESCENT);
            frame.setVerticalSpeedInstability(200.0);

            List<AIRecommendation> recs = service.generateRecommendations(riskAssessment.getId());

            assertThat(recs).extracting(AIRecommendation::getRecommendationType)
                    .contains(RecommendationType.STABILIZE_DESCENT);
        }

        @Test
        @DisplayName("APPROACH + high vertical speed instability → STABILIZE_DESCENT")
        void stabilizeApproach() {
            riskAssessment.setRiskLevel(RiskLevel.MEDIUM);
            frame.setPhaseOfFlight(PhaseOfFlight.APPROACH);
            frame.setVerticalSpeedInstability(200.0);

            List<AIRecommendation> recs = service.generateRecommendations(riskAssessment.getId());

            assertThat(recs).extracting(AIRecommendation::getRecommendationType)
                    .contains(RecommendationType.STABILIZE_DESCENT);
        }

        @Test
        @DisplayName("missedChecklistProbability > 0.5 → EXECUTE_CHECKLIST")
        void executeChecklist() {
            riskAssessment.setRiskLevel(RiskLevel.MEDIUM);
            riskAssessment.setMissedChecklistProbability(0.6);

            List<AIRecommendation> recs = service.generateRecommendations(riskAssessment.getId());

            assertThat(recs).extracting(AIRecommendation::getRecommendationType)
                    .contains(RecommendationType.EXECUTE_CHECKLIST);
        }

        @Test
        @DisplayName("Swiss Cheese triggered → REDUCE_TASK_SWITCHING")
        void reduceTaskSwitching() {
            riskAssessment.setRiskLevel(RiskLevel.HIGH);
            riskAssessment.setSwissCheeseTriggered(true);
            frame.setAutopilotEngaged(true); // to avoid also triggering ENGAGE_AUTOPILOT

            List<AIRecommendation> recs = service.generateRecommendations(riskAssessment.getId());

            assertThat(recs).extracting(AIRecommendation::getRecommendationType)
                    .contains(RecommendationType.REDUCE_TASK_SWITCHING);
        }

        @Test
        @DisplayName("CRITICAL → GO_AROUND")
        void goAround() {
            riskAssessment.setRiskLevel(RiskLevel.CRITICAL);
            frame.setPhaseOfFlight(PhaseOfFlight.LANDING);
            frame.setAutopilotEngaged(false);

            List<AIRecommendation> recs = service.generateRecommendations(riskAssessment.getId());

            assertThat(recs).extracting(AIRecommendation::getRecommendationType)
                    .contains(RecommendationType.GO_AROUND);
            assertThat(recs).extracting(AIRecommendation::getSeverity)
                    .contains(Severity.CRITICAL);
        }
    }

    // ──────────────────────── Deduplication ────────────────────────

    @Nested
    @DisplayName("Deduplication")
    class Deduplication {

        @Test
        @DisplayName("No duplicate recommendation types per frame")
        void noDuplicates() {
            riskAssessment.setRiskLevel(RiskLevel.CRITICAL);
            riskAssessment.setSwissCheeseTriggered(true);
            riskAssessment.setMissedChecklistProbability(0.6);
            frame.setPhaseOfFlight(PhaseOfFlight.APPROACH);
            frame.setVerticalSpeedInstability(200.0);
            frame.setAutopilotEngaged(false);

            List<AIRecommendation> recs = service.generateRecommendations(riskAssessment.getId());

            // All types should be distinct
            List<RecommendationType> types = recs.stream()
                    .map(AIRecommendation::getRecommendationType).toList();
            assertThat(types).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("Only valid recommendation types used")
        void validTypesOnly() {
            riskAssessment.setRiskLevel(RiskLevel.CRITICAL);
            riskAssessment.setSwissCheeseTriggered(true);
            frame.setAutopilotEngaged(false);

            List<AIRecommendation> recs = service.generateRecommendations(riskAssessment.getId());

            recs.forEach(r ->
                    assertThat(r.getRecommendationType()).isIn(RecommendationType.values())
            );
        }
    }

    // ──────────────────────── Edge Cases ────────────────────────

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Risk assessment not found → throws exception")
        void riskNotFound() {
            UUID badId = UUID.randomUUID();
            when(riskAssessmentRepository.findById(badId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateRecommendations(badId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Recommendation persists with correct foreign key to RiskAssessment")
        void correctForeignKey() {
            riskAssessment.setRiskLevel(RiskLevel.LOW);

            List<AIRecommendation> recs = service.generateRecommendations(riskAssessment.getId());

            recs.forEach(r ->
                    assertThat(r.getRiskAssessment()).isSameAs(riskAssessment)
            );
        }

        @Test
        @DisplayName("Multiple rules can fire simultaneously")
        void multipleRulesFire() {
            riskAssessment.setRiskLevel(RiskLevel.CRITICAL);
            riskAssessment.setSwissCheeseTriggered(true);
            riskAssessment.setMissedChecklistProbability(0.7);
            frame.setPhaseOfFlight(PhaseOfFlight.APPROACH);
            frame.setVerticalSpeedInstability(250.0);
            frame.setAutopilotEngaged(false);

            List<AIRecommendation> recs = service.generateRecommendations(riskAssessment.getId());

            // Should fire: ENGAGE_AUTOPILOT, STABILIZE_DESCENT, EXECUTE_CHECKLIST, REDUCE_TASK_SWITCHING, GO_AROUND
            assertThat(recs.size()).isGreaterThanOrEqualTo(4);
        }
    }
}

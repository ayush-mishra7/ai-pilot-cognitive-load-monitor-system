package com.aipclm.system.risk.service;

import com.aipclm.system.TestFixtures;
import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.model.RiskLevel;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.risk.repository.RiskAssessmentRepository;
import com.aipclm.system.session.model.FlightSession;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RiskEngineService")
class RiskEngineServiceTest {

    @Mock private CognitiveStateRepository cognitiveStateRepository;
    @Mock private TelemetryFrameRepository telemetryFrameRepository;
    @Mock private RiskAssessmentRepository riskAssessmentRepository;

    @InjectMocks private RiskEngineService service;

    private Pilot pilot;
    private FlightSession session;
    private TelemetryFrame frame;
    private CognitiveState cogState;

    @BeforeEach
    void setUp() {
        pilot = TestFixtures.pilotNovice();
        session = TestFixtures.runningSession(pilot);
        frame = TestFixtures.cruiseFrame(session, 1);
        cogState = TestFixtures.cognitiveState(frame, 30, 30, 0.85);

        // Default stubs (lenient to avoid UnnecessaryStubbing in tests that use different IDs)
        lenient().when(cognitiveStateRepository.findById(cogState.getId())).thenReturn(Optional.of(cogState));
        lenient().when(riskAssessmentRepository.save(any())).thenAnswer(inv -> {
            RiskAssessment ra = inv.getArgument(0);
            ra.setId(UUID.randomUUID());
            return ra;
        });
        lenient().when(cognitiveStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubPreviousRisk(RiskLevel level) {
        RiskAssessment prev = TestFixtures.riskAssessment(cogState, level, false);
        when(riskAssessmentRepository
                .findTopByCognitiveStateTelemetryFrameFlightSessionIdOrderByTimestampDesc(session.getId()))
                .thenReturn(Optional.of(prev));
    }

    private void stubSmoothedLoad(double load) {
        CognitiveState cs = TestFixtures.cognitiveState(frame, load, load, 0.85);
        when(cognitiveStateRepository.findTop5BySessionIdOrderByTimestampDesc(session.getId()))
                .thenReturn(List.of(cs, cs, cs, cs, cs));
    }

    // ──────────────────────── Risk Classification ────────────────────────

    @Nested
    @DisplayName("Risk Classification")
    class RiskClassification {

        @Test
        @DisplayName("LOW when smoothedLoad < 40")
        void lowRisk() {
            stubSmoothedLoad(25.0);
            stubPreviousRisk(RiskLevel.LOW);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        }

        @Test
        @DisplayName("MEDIUM when smoothedLoad >= 40 (from LOW)")
        void mediumRisk() {
            stubSmoothedLoad(45.0);
            stubPreviousRisk(RiskLevel.LOW);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("HIGH when smoothedLoad >= 60 (from MEDIUM)")
        void highRisk() {
            stubSmoothedLoad(65.0);
            stubPreviousRisk(RiskLevel.MEDIUM);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        }

        @Test
        @DisplayName("CRITICAL when smoothedLoad >= 80 (from HIGH)")
        void criticalRisk() {
            cogState.setConfidenceScore(0.85);
            stubSmoothedLoad(85.0);
            stubPreviousRisk(RiskLevel.HIGH);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
        }
    }

    // ──────────────────────── Hysteresis ────────────────────────

    @Nested
    @DisplayName("Hysteresis")
    class Hysteresis {

        @Test
        @DisplayName("Does not jump LOW → HIGH in one step")
        void noDirectJumpLowToHigh() {
            stubSmoothedLoad(65.0);
            stubPreviousRisk(RiskLevel.LOW);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            // Even though load is 65, from LOW it only goes to MEDIUM (>=40)
            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("Does not downgrade MEDIUM → LOW until smoothed < 35")
        void hysteresisDowngrade() {
            stubSmoothedLoad(37.0); // above 35 threshold for downgrade
            stubPreviousRisk(RiskLevel.MEDIUM);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("Downgrades MEDIUM → LOW when smoothed < 35")
        void downgradeWhenBelow35() {
            stubSmoothedLoad(30.0);
            stubPreviousRisk(RiskLevel.MEDIUM);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        }

        @Test
        @DisplayName("Downgrades HIGH → MEDIUM when smoothed < 55")
        void downgradeHighToMedium() {
            stubSmoothedLoad(50.0);
            stubPreviousRisk(RiskLevel.HIGH);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("Stays HIGH when smoothed is 55-79 (above downgrade, below critical)")
        void staysHigh() {
            stubSmoothedLoad(70.0);
            stubPreviousRisk(RiskLevel.HIGH);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        }

        @Test
        @DisplayName("Downgrades CRITICAL → HIGH when smoothed < 75")
        void downgradeCriticalToHigh() {
            stubSmoothedLoad(70.0);
            stubPreviousRisk(RiskLevel.CRITICAL);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        }

        @Test
        @DisplayName("Stays CRITICAL when smoothed >= 75")
        void staysCritical() {
            cogState.setConfidenceScore(0.85);
            stubSmoothedLoad(78.0);
            stubPreviousRisk(RiskLevel.CRITICAL);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
        }
    }

    // ──────────────────────── Swiss Cheese Model ────────────────────────

    @Nested
    @DisplayName("Swiss Cheese Model")
    class SwissCheeseModel {

        @Test
        @DisplayName("All 4 barriers breached → swissCheeseTriggered = true")
        void allBarriersBreached() {
            // smoothedLoad > 70, fatigueIndex > 60, errorCount > 2, turbulenceLevel > 0.05
            TelemetryFrame badFrame = TestFixtures.highLoadFrame(session, 1);
            CognitiveState badCog = TestFixtures.cognitiveState(badFrame, 75, 75, 0.85);
            when(cognitiveStateRepository.findById(badCog.getId())).thenReturn(Optional.of(badCog));

            CognitiveState smoothedCs = TestFixtures.cognitiveState(badFrame, 75, 75, 0.85);
            when(cognitiveStateRepository.findTop5BySessionIdOrderByTimestampDesc(session.getId()))
                    .thenReturn(List.of(smoothedCs, smoothedCs, smoothedCs, smoothedCs, smoothedCs));
            stubPreviousRisk(RiskLevel.HIGH);

            RiskAssessment result = service.evaluateRisk(badCog.getId());

            assertThat(result.isSwissCheeseTriggered()).isTrue();
        }

        @Test
        @DisplayName("Single moderate risk → swissCheeseTriggered = false")
        void singleBarrierNotEnough() {
            // Only high fatigue, but low errors, low turbulence; smoothed load < 70
            frame.setFatigueIndex(80);
            frame.setErrorCount(0);
            frame.setTurbulenceLevel(0.01);
            cogState = TestFixtures.cognitiveState(frame, 30, 30, 0.85);
            when(cognitiveStateRepository.findById(cogState.getId())).thenReturn(Optional.of(cogState));

            stubSmoothedLoad(30.0);
            stubPreviousRisk(RiskLevel.LOW);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.isSwissCheeseTriggered()).isFalse();
        }
    }

    // ──────────────────────── Confidence Gate ────────────────────────

    @Nested
    @DisplayName("Confidence Gate")
    class ConfidenceGate {

        @Test
        @DisplayName("Confidence < 0.7 blocks CRITICAL → caps at HIGH")
        void lowConfidenceBlocksCritical() {
            cogState.setConfidenceScore(0.5);
            stubSmoothedLoad(90.0);
            stubPreviousRisk(RiskLevel.HIGH);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isNotEqualTo(RiskLevel.CRITICAL);
            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        }

        @Test
        @DisplayName("ML fallback mode (confidence=0.5) never produces CRITICAL")
        void fallbackNeverCritical() {
            cogState.setConfidenceScore(0.5); // fallback confidence
            stubSmoothedLoad(99.0);
            stubPreviousRisk(RiskLevel.HIGH);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isNotEqualTo(RiskLevel.CRITICAL);
        }

        @Test
        @DisplayName("Confidence >= 0.7 allows CRITICAL")
        void highConfidenceAllowsCritical() {
            cogState.setConfidenceScore(0.85);
            stubSmoothedLoad(85.0);
            stubPreviousRisk(RiskLevel.HIGH);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
        }
    }

    // ──────────────────────── Edge Cases ────────────────────────

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Missing cognitive state → throws exception")
        void missingCognitiveState() {
            UUID badId = UUID.randomUUID();
            when(cognitiveStateRepository.findById(badId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.evaluateRisk(badId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("SmoothedLoad persisted back into CognitiveState")
        void smoothedLoadPersisted() {
            stubSmoothedLoad(42.5);
            stubPreviousRisk(RiskLevel.LOW);

            service.evaluateRisk(cogState.getId());

            ArgumentCaptor<CognitiveState> captor = ArgumentCaptor.forClass(CognitiveState.class);
            verify(cognitiveStateRepository).save(captor.capture());
            assertThat(captor.getValue().getSmoothedLoad()).isEqualTo(42.5);
        }

        @Test
        @DisplayName("No previous risk assessment → defaults to LOW")
        void noPreviousRiskDefaultsLow() {
            stubSmoothedLoad(25.0);
            when(riskAssessmentRepository
                    .findTopByCognitiveStateTelemetryFrameFlightSessionIdOrderByTimestampDesc(session.getId()))
                    .thenReturn(Optional.empty());

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        }

        @Test
        @DisplayName("Single-frame anomaly does not escalate risk (smoothing absorbs it)")
        void singleFrameAnomaly() {
            // 4 normal frames + 1 extreme frame → average smoothed
            CognitiveState normal = TestFixtures.cognitiveState(frame, 25, 25, 0.85);
            CognitiveState extreme = TestFixtures.cognitiveState(frame, 95, 95, 0.85);
            when(cognitiveStateRepository.findTop5BySessionIdOrderByTimestampDesc(session.getId()))
                    .thenReturn(List.of(extreme, normal, normal, normal, normal));
            stubPreviousRisk(RiskLevel.LOW);

            RiskAssessment result = service.evaluateRisk(cogState.getId());

            // Average: (95 + 25*4) / 5 = 39 → still LOW (below 40 threshold)
            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        }
    }
}

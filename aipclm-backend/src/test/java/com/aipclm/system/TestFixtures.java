package com.aipclm.system;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.model.RiskLevel;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.pilot.model.PilotProfileType;
import com.aipclm.system.risk.model.RiskAssessment;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.model.FlightSessionStatus;
import com.aipclm.system.telemetry.model.PhaseOfFlight;
import com.aipclm.system.telemetry.model.TelemetryFrame;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared factory methods for building valid test entities.
 * All builders produce a fully-populated, JPA-safe entity.
 */
public final class TestFixtures {

    private TestFixtures() {}

    public static Pilot pilotNovice() {
        return Pilot.builder()
                .id(UUID.randomUUID())
                .fullName("Test Pilot NOVICE")
                .profileType(PilotProfileType.NOVICE)
                .baselineStressSensitivity(1.0)
                .baselineFatigueRate(1.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static Pilot pilotExperienced() {
        return Pilot.builder()
                .id(UUID.randomUUID())
                .fullName("Test Pilot EXPERIENCED")
                .profileType(PilotProfileType.EXPERIENCED)
                .baselineStressSensitivity(1.0)
                .baselineFatigueRate(1.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static FlightSession runningSession(Pilot pilot) {
        return FlightSession.builder()
                .id(UUID.randomUUID())
                .pilot(pilot)
                .sessionStartTime(Instant.now())
                .status(FlightSessionStatus.RUNNING)
                .frameFrequencySeconds(2)
                .totalFramesGenerated(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static FlightSession completedSession(Pilot pilot) {
        FlightSession s = runningSession(pilot);
        s.setStatus(FlightSessionStatus.COMPLETED);
        return s;
    }

    public static TelemetryFrame cruiseFrame(FlightSession session, int frameNumber) {
        return TelemetryFrame.builder()
                .id(UUID.randomUUID())
                .flightSession(session)
                .frameNumber(frameNumber)
                .timestamp(Instant.now())
                .phaseOfFlight(PhaseOfFlight.CRUISE)
                .altitude(30000)
                .airspeed(450)
                .verticalSpeed(0)
                .heading(270)
                .pitch(0)
                .roll(0)
                .yawRate(0)
                .turbulenceLevel(0.2)
                .weatherSeverity(0.2)
                .autopilotEngaged(true)
                .reactionTimeMs(350)
                .controlInputFrequency(0.5)
                .checklistDelaySeconds(2.0)
                .taskSwitchRate(1.0)
                .errorCount(0)
                .controlJitterIndex(0.1)
                .instrumentScanVariance(0.8)
                .heartRate(75)
                .blinkRate(18)
                .fatigueIndex(10)
                .stressIndex(20)
                .altitudeDeviation(50)
                .verticalSpeedInstability(30)
                .airspeedDeviation(5)
                .headingDeviation(2)
                .pitchInstability(1)
                .rollInstability(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static TelemetryFrame approachFrame(FlightSession session, int frameNumber) {
        TelemetryFrame f = cruiseFrame(session, frameNumber);
        f.setPhaseOfFlight(PhaseOfFlight.APPROACH);
        f.setAltitude(3000);
        f.setAirspeed(180);
        f.setVerticalSpeed(-800);
        f.setAutopilotEngaged(false);
        f.setTurbulenceLevel(0.3);
        f.setFatigueIndex(40);
        f.setStressIndex(50);
        f.setReactionTimeMs(500);
        f.setVerticalSpeedInstability(200);
        return f;
    }

    public static TelemetryFrame highLoadFrame(FlightSession session, int frameNumber) {
        TelemetryFrame f = approachFrame(session, frameNumber);
        f.setPhaseOfFlight(PhaseOfFlight.LANDING);
        f.setReactionTimeMs(1200);
        f.setErrorCount(4);
        f.setTurbulenceLevel(0.5);
        f.setStressIndex(85);
        f.setFatigueIndex(75);
        f.setHeartRate(130);
        f.setBlinkRate(12);
        f.setControlJitterIndex(0.7);
        f.setChecklistDelaySeconds(20);
        f.setTaskSwitchRate(8);
        f.setVerticalSpeedInstability(300);
        return f;
    }

    public static CognitiveState cognitiveState(TelemetryFrame frame, double expertLoad, double mlLoad,
                                                 double confidence) {
        return CognitiveState.builder()
                .id(UUID.randomUUID())
                .telemetryFrame(frame)
                .expertComputedLoad(expertLoad)
                .mlPredictedLoad(mlLoad)
                .errorProbability(mlLoad / 100.0)
                .confidenceScore(confidence)
                .smoothedLoad(0)
                .fatigueTrendSlope(0)
                .swissCheeseAlignmentScore(0)
                .advisoryGenerated(false)
                .riskLevel(RiskLevel.LOW)
                .timestamp(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static RiskAssessment riskAssessment(CognitiveState cogState, RiskLevel level, boolean swissCheese) {
        return RiskAssessment.builder()
                .id(UUID.randomUUID())
                .cognitiveState(cogState)
                .riskLevel(level)
                .aggregatedRiskScore(50)
                .delayedReactionProbability(0.3)
                .unsafeDescentProbability(0.2)
                .missedChecklistProbability(0.1)
                .riskEscalated(false)
                .swissCheeseTriggered(swissCheese)
                .timestamp(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}

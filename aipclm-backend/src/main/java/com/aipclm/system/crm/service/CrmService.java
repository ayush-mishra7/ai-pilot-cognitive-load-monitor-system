package com.aipclm.system.crm.service;

import com.aipclm.system.cognitive.model.CognitiveState;
import com.aipclm.system.cognitive.repository.CognitiveStateRepository;
import com.aipclm.system.crm.model.CrewAssignment;
import com.aipclm.system.crm.model.CrmAssessment;
import com.aipclm.system.crm.repository.CrewAssignmentRepository;
import com.aipclm.system.crm.repository.CrmAssessmentRepository;
import com.aipclm.system.pilot.model.CrewRole;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.pilot.model.PilotProfileType;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.repository.FlightSessionRepository;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Crew Resource Management service that computes cross-crew interaction metrics.
 *
 * <h3>CRM Metrics</h3>
 * <ul>
 *   <li><b>Communication Score</b> — degrades when cognitive load diverges or errors accumulate</li>
 *   <li><b>Workload Distribution</b> — 0.5 = perfectly shared, 0/1 = one-sided</li>
 *   <li><b>Authority Gradient</b> — steep when captain is much more experienced than FO</li>
 *   <li><b>Situational Awareness</b> — drops under combined fatigue and stress</li>
 *   <li><b>Cross-Crew Stress Contagion</b> — fraction of partner stress that propagates</li>
 *   <li><b>CRM Effectiveness</b> — weighted composite of all metrics</li>
 *   <li><b>Fatigue Symmetry</b> — 1 = identical fatigue, 0 = maximally divergent</li>
 * </ul>
 *
 * <h3>Cross-Crew Propagation (applied during frame generation)</h3>
 * <ul>
 *   <li>Stress contagion factor: 0.15 of partner's excess stress propagates</li>
 *   <li>Fatigue convergence factor: 0.10 — fatigue levels slowly converge</li>
 * </ul>
 */
@Service
@Slf4j
public class CrmService {

    /** Fraction of partner's excess stress that propagates per tick. */
    public static final double STRESS_CONTAGION_FACTOR = 0.15;
    /** Rate at which crew fatigue levels converge per tick. */
    public static final double FATIGUE_CONVERGENCE_FACTOR = 0.10;

    private final CrmAssessmentRepository crmAssessmentRepository;
    private final CrewAssignmentRepository crewAssignmentRepository;
    private final TelemetryFrameRepository telemetryFrameRepository;
    private final CognitiveStateRepository cognitiveStateRepository;
    private final FlightSessionRepository flightSessionRepository;

    public CrmService(CrmAssessmentRepository crmAssessmentRepository,
                      CrewAssignmentRepository crewAssignmentRepository,
                      TelemetryFrameRepository telemetryFrameRepository,
                      CognitiveStateRepository cognitiveStateRepository,
                      FlightSessionRepository flightSessionRepository) {
        this.crmAssessmentRepository = crmAssessmentRepository;
        this.crewAssignmentRepository = crewAssignmentRepository;
        this.telemetryFrameRepository = telemetryFrameRepository;
        this.cognitiveStateRepository = cognitiveStateRepository;
        this.flightSessionRepository = flightSessionRepository;
    }

    /**
     * Computes cross-crew stress propagation values based on the partner pilot's
     * previous telemetry frame. Returns a two-element double array:
     * [stressDelta, fatigueDelta] to be added to the current pilot's baselines.
     *
     * @param sessionId the flight session
     * @param myRole    the role of the pilot being generated
     * @return [stressDelta, fatigueDelta] — both >= 0 or negative for convergence
     */
    public double[] computeCrossCrewPropagation(UUID sessionId, CrewRole myRole) {
        CrewRole partnerRole = (myRole == CrewRole.CAPTAIN) ? CrewRole.FIRST_OFFICER : CrewRole.CAPTAIN;

        Optional<TelemetryFrame> partnerFrameOpt = telemetryFrameRepository
                .findTopByFlightSessionIdAndCrewRoleOrderByFrameNumberDesc(sessionId, partnerRole);

        if (partnerFrameOpt.isEmpty()) {
            return new double[]{0.0, 0.0}; // First frame — no partner data yet
        }

        TelemetryFrame partnerFrame = partnerFrameOpt.get();

        // Stress contagion: if partner is stressed (>50), excess propagates
        double partnerStress = partnerFrame.getStressIndex();
        double stressDelta = Math.max(0, (partnerStress - 50.0) * STRESS_CONTAGION_FACTOR);

        // Fatigue convergence: pull toward partner's fatigue level
        double partnerFatigue = partnerFrame.getFatigueIndex();
        // This will be combined with the pilot's own fatigue later
        double fatigueDelta = partnerFatigue * FATIGUE_CONVERGENCE_FACTOR;

        return new double[]{stressDelta, fatigueDelta};
    }

    /**
     * Evaluates CRM metrics after both crew members' cognitive states are computed.
     *
     * @param sessionId   the flight session
     * @param frameNumber the current frame number
     * @return the persisted CrmAssessment
     */
    public CrmAssessment evaluateCrm(UUID sessionId, int frameNumber) {
        FlightSession session = flightSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));

        // Load both crew frames for this tick
        List<TelemetryFrame> frames = telemetryFrameRepository
                .findByFlightSessionIdAndFrameNumber(sessionId, frameNumber);

        TelemetryFrame captainFrame = frames.stream()
                .filter(f -> f.getCrewRole() == CrewRole.CAPTAIN).findFirst().orElse(null);
        TelemetryFrame foFrame = frames.stream()
                .filter(f -> f.getCrewRole() == CrewRole.FIRST_OFFICER).findFirst().orElse(null);

        if (captainFrame == null || foFrame == null) {
            throw new IllegalStateException("Missing crew frames for session=" + sessionId
                    + " frame=" + frameNumber);
        }

        // Load cognitive states
        CognitiveState captainCog = cognitiveStateRepository
                .findByTelemetryFrameId(captainFrame.getId()).orElse(null);
        CognitiveState foCog = cognitiveStateRepository
                .findByTelemetryFrameId(foFrame.getId()).orElse(null);

        double captainLoad = captainCog != null ? captainCog.getSmoothedLoad() : 50.0;
        double foLoad = foCog != null ? foCog.getSmoothedLoad() : 50.0;

        // Load crew assignments for profile-based authority gradient
        List<CrewAssignment> assignments = crewAssignmentRepository.findByFlightSessionId(sessionId);
        Pilot captainPilot = assignments.stream()
                .filter(a -> a.getCrewRole() == CrewRole.CAPTAIN)
                .map(CrewAssignment::getPilot).findFirst().orElse(null);
        Pilot foPilot = assignments.stream()
                .filter(a -> a.getCrewRole() == CrewRole.FIRST_OFFICER)
                .map(CrewAssignment::getPilot).findFirst().orElse(null);

        // ── Communication Score ──
        // Degrades with load divergence and combined errors
        double loadDivergence = Math.abs(captainLoad - foLoad);
        int combinedErrors = captainFrame.getErrorCount() + foFrame.getErrorCount();
        double communicationScore = Math.max(0, Math.min(100,
                100.0 - loadDivergence * 0.8 - combinedErrors * 8.0));

        // ── Workload Distribution ──
        double totalLoad = captainLoad + foLoad;
        double workloadDistribution = totalLoad > 0 ? captainLoad / totalLoad : 0.5;

        // ── Authority Gradient ──
        double authorityGradient = computeAuthorityGradient(captainPilot, foPilot);

        // ── Situational Awareness ──
        double maxFatigue = Math.max(captainFrame.getFatigueIndex(), foFrame.getFatigueIndex());
        double maxStress = Math.max(captainFrame.getStressIndex(), foFrame.getStressIndex());
        double situationalAwareness = Math.max(0, Math.min(100,
                100.0 - maxFatigue * 0.4 - maxStress * 0.3 - combinedErrors * 5.0));

        // ── Cross-Crew Stress Contagion ──
        double stressDiff = Math.abs(captainFrame.getStressIndex() - foFrame.getStressIndex());
        double crossCrewContagion = Math.min(1.0, stressDiff / 100.0);

        // ── Fatigue Symmetry ──
        double fatigueDiff = Math.abs(captainFrame.getFatigueIndex() - foFrame.getFatigueIndex());
        double fatigueSymmetry = Math.max(0, 1.0 - fatigueDiff / 100.0);

        // ── CRM Effectiveness (weighted composite) ──
        double crmEffectiveness = communicationScore * 0.25
                + (1.0 - Math.abs(workloadDistribution - 0.5) * 2.0) * 100.0 * 0.20
                + situationalAwareness * 0.25
                + fatigueSymmetry * 100.0 * 0.15
                + (1.0 - crossCrewContagion) * 100.0 * 0.15;
        crmEffectiveness = Math.max(0, Math.min(100, crmEffectiveness));

        CrmAssessment assessment = CrmAssessment.builder()
                .flightSession(session)
                .frameNumber(frameNumber)
                .communicationScore(communicationScore)
                .workloadDistribution(workloadDistribution)
                .authorityGradient(authorityGradient)
                .situationalAwarenessScore(situationalAwareness)
                .crossCrewStressContagion(crossCrewContagion)
                .crmEffectivenessScore(crmEffectiveness)
                .fatigueSymmetry(fatigueSymmetry)
                .captainLoad(captainLoad)
                .firstOfficerLoad(foLoad)
                .timestamp(Instant.now())
                .build();

        crmAssessmentRepository.save(assessment);

        log.info("[CRM] Frame={} comm={} workload={} SA={} CRM={} captLoad={} foLoad={}",
                frameNumber,
                String.format("%.1f", communicationScore),
                String.format("%.2f", workloadDistribution),
                String.format("%.1f", situationalAwareness),
                String.format("%.1f", crmEffectiveness),
                String.format("%.1f", captainLoad),
                String.format("%.1f", foLoad));

        return assessment;
    }

    /**
     * Computes authority gradient based on pilot experience profiles.
     * 0 = flat (equal experience), 1 = steep (large experience gap).
     */
    private double computeAuthorityGradient(Pilot captain, Pilot fo) {
        if (captain == null || fo == null) return 0.5;

        int captainExp = profileExperienceLevel(captain.getProfileType());
        int foExp = profileExperienceLevel(fo.getProfileType());

        // Normalize difference to 0-1 range (max gap = 3)
        return Math.min(1.0, Math.abs(captainExp - foExp) / 3.0);
    }

    private int profileExperienceLevel(PilotProfileType type) {
        return switch (type) {
            case NOVICE -> 1;
            case FATIGUE_PRONE -> 2;
            case HIGH_STRESS -> 2;
            case EXPERIENCED -> 4;
        };
    }
}

package com.aipclm.system.simulation.service;

import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.pilot.repository.PilotRepository;
import com.aipclm.system.scenario.model.*;
import com.aipclm.system.scenario.repository.FlightScenarioRepository;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.model.FlightSessionStatus;
import com.aipclm.system.session.repository.FlightSessionRepository;
import com.aipclm.system.telemetry.model.PhaseOfFlight;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class SimulationEngineService {

    private final FlightSessionRepository flightSessionRepository;
    private final PilotRepository pilotRepository;
    private final TelemetryFrameRepository telemetryFrameRepository;
    private final FlightScenarioRepository scenarioRepository;

    private static final java.util.Random RANDOM = new java.util.Random(42); // Seeded for determinism

    public SimulationEngineService(FlightSessionRepository flightSessionRepository,
            PilotRepository pilotRepository,
            TelemetryFrameRepository telemetryFrameRepository,
            FlightScenarioRepository scenarioRepository) {
        this.flightSessionRepository = flightSessionRepository;
        this.pilotRepository = pilotRepository;
        this.telemetryFrameRepository = telemetryFrameRepository;
        this.scenarioRepository = scenarioRepository;
    }

    @Transactional
    public void generateNextFrame(UUID sessionId) {
        log.info("Generating next telemetry frame for session: {}", sessionId);

        FlightSession session = flightSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() != FlightSessionStatus.RUNNING) {
            log.warn("Cannot generate frame for session {}; status is {}, expected RUNNING",
                    sessionId, session.getStatus());
            return;
        }

        Pilot pilot = pilotRepository.findById(session.getPilot().getId())
                .orElseThrow(() -> new IllegalStateException("Pilot not found for session"));

        // Load scenario if one exists for this session (nullable — backwards compatible)
        FlightScenario scenario = scenarioRepository.findByFlightSessionId(sessionId).orElse(null);

        int nextFrameNumber = session.getTotalFramesGenerated() + 1;

        // Duplicate-frame guard: if a frame with this number already exists, skip
        TelemetryFrame existingLatest = telemetryFrameRepository
                .findTopByFlightSessionIdOrderByFrameNumberDesc(sessionId).orElse(null);
        if (existingLatest != null && existingLatest.getFrameNumber() >= nextFrameNumber) {
            log.warn("Duplicate frame attempt for session {}; expected frame {} but latest is {}. Skipping.",
                    sessionId, nextFrameNumber, existingLatest.getFrameNumber());
            return;
        }

        int currentSimulationTimeSeconds = nextFrameNumber * session.getFrameFrequencySeconds();

        PhaseOfFlight phaseOfFlight = determinePhaseOfFlight(currentSimulationTimeSeconds);

        log.debug("Session Time: {}s, Phase: {}, Frame: {}",
                currentSimulationTimeSeconds, phaseOfFlight, nextFrameNumber);

        // Baseline generation based on phase
        double baseAltitude = 0.0;
        double baseVerticalSpeed = 0.0;
        double baseAirspeed = 0.0;
        double baseHeading = 270.0;
        double baseTurbulence = 0.1;

        switch (phaseOfFlight) {
            case TAKEOFF:
                baseAltitude = (currentSimulationTimeSeconds / 300.0) * 5000;
                baseVerticalSpeed = 1500.0;
                baseAirspeed = 150.0 + (currentSimulationTimeSeconds / 300.0) * 100;
                break;
            case CLIMB:
                baseAltitude = 5000 + ((currentSimulationTimeSeconds - 300) / 300.0) * 25000;
                baseVerticalSpeed = 2000.0;
                baseAirspeed = 250.0;
                break;
            case CRUISE:
                baseAltitude = 30000.0;
                baseVerticalSpeed = 0.0;
                baseAirspeed = 450.0;
                baseTurbulence = 0.2; // Slightly higher at cruise altitude
                break;
            case DESCENT:
                baseAltitude = 30000.0 - ((currentSimulationTimeSeconds - 1800) / 600.0) * 25000;
                baseVerticalSpeed = -1500.0;
                baseAirspeed = 300.0;
                break;
            case APPROACH:
                baseAltitude = 5000.0 - ((currentSimulationTimeSeconds - 2400) / 300.0) * 5000;
                baseVerticalSpeed = -800.0;
                baseAirspeed = 180.0;
                baseTurbulence = 0.3; // Higher turbulence near ground
                break;
            case LANDING:
                baseAltitude = 0.0;
                baseVerticalSpeed = -100.0;
                baseAirspeed = 140.0;
                break;
        }

        // Apply Gaussian noise
        double altitude = Math.max(0, baseAltitude + RANDOM.nextGaussian() * 50);
        double verticalSpeed = baseVerticalSpeed + RANDOM.nextGaussian() * 100;
        double airspeed = Math.max(0, baseAirspeed + RANDOM.nextGaussian() * 5);
        double heading = (baseHeading + RANDOM.nextGaussian() * 2) % 360;
        double pitch = (verticalSpeed / 100.0) + RANDOM.nextGaussian() * 1.5;
        double roll = RANDOM.nextGaussian() * 2.0;
        double yawRate = RANDOM.nextGaussian() * 0.5;
        double turbulenceLevel = Math.max(0, Math.min(1.0, baseTurbulence + RANDOM.nextGaussian() * 0.05));
        double weatherSeverity = 0.2; // Static for now
        boolean autopilotEngaged = phaseOfFlight == PhaseOfFlight.CRUISE || phaseOfFlight == PhaseOfFlight.CLIMB;

        /* ═══════════════════════════════════════════════════════
         *  SCENARIO MODIFIERS — applied after baseline + noise
         * ═══════════════════════════════════════════════════════ */
        double scenarioStressBonus = 0.0;
        double scenarioFatigueMultiplier = 1.0;

        if (scenario != null) {
            // ── Weather → turbulence multiplier ──
            double turbulenceMultiplier = switch (scenario.getWeatherCondition()) {
                case THUNDERSTORM -> 3.0;
                case ICE          -> 2.5;
                case SNOW         -> 2.0;
                case RAIN         -> 1.5;
                case OVERCAST     -> 1.2;
                case FOG          -> 1.0;   // no extra turbulence but visibility penalty
                case CLOUDY       -> 1.1;
                case CLEAR        -> 1.0;
            };
            turbulenceLevel = Math.min(1.0, turbulenceLevel * turbulenceMultiplier);

            // ── Weather → weatherSeverity (replaces static 0.2) ──
            weatherSeverity = switch (scenario.getWeatherCondition()) {
                case CLEAR -> 0.05;
                case CLOUDY -> 0.15;
                case OVERCAST -> 0.25;
                case RAIN -> 0.45;
                case FOG -> 0.40;
                case SNOW -> 0.55;
                case ICE -> 0.70;
                case THUNDERSTORM -> 0.90;
            };

            // ── Visibility → stress bonus ──
            scenarioStressBonus += switch (scenario.getVisibility()) {
                case UNLIMITED -> 0.0;
                case GOOD      -> 2.0;
                case MODERATE  -> 8.0;
                case LOW       -> 20.0;
                case VERY_LOW  -> 30.0;
                case ZERO      -> 40.0;
            };

            // ── Time of Day → stress bonus & fatigue ──
            if (scenario.getTimeOfDay() == TimeOfDay.NIGHT) {
                scenarioStressBonus += 15.0;
                scenarioFatigueMultiplier *= 1.15;
            } else if (scenario.getTimeOfDay() == TimeOfDay.DUSK) {
                scenarioStressBonus += 8.0;
                scenarioFatigueMultiplier *= 1.05;
            }

            // ── Wind → heading deviation & roll ──
            double windEffect = scenario.getWindSpeedKnots() / 80.0; // normalize 0–1
            heading = (heading + scenario.getCrosswindComponent() * 0.3 * RANDOM.nextGaussian()) % 360;
            roll += scenario.getCrosswindComponent() * 0.1 * RANDOM.nextGaussian();
            yawRate += windEffect * RANDOM.nextGaussian() * 1.5;

            // ── Terrain → turbulence + altitude floor ──
            if (scenario.getTerrainType() == TerrainType.MOUNTAINOUS) {
                turbulenceLevel = Math.min(1.0, turbulenceLevel + 0.15);
                altitude = Math.max(altitude, scenario.getAirportElevationFt() + 1000);
            } else if (scenario.getTerrainType() == TerrainType.URBAN) {
                turbulenceLevel = Math.min(1.0, turbulenceLevel + 0.05);
            }

            // ── Runway → landing difficulty (affects approach/landing) ──
            if ((phaseOfFlight == PhaseOfFlight.APPROACH || phaseOfFlight == PhaseOfFlight.LANDING)
                    && scenario.getRunwayCondition() != RunwayCondition.DRY) {
                double runwayPenalty = switch (scenario.getRunwayCondition()) {
                    case WET -> 0.10;
                    case CONTAMINATED -> 0.20;
                    case ICY -> 0.30;
                    case FLOODED -> 0.35;
                    default -> 0.0;
                };
                scenarioStressBonus += runwayPenalty * 50;
                airspeed += runwayPenalty * 10; // harder to slow down
            }

            // ── Mission type → base stress ──
            scenarioStressBonus += switch (scenario.getMissionType()) {
                case ROUTINE   -> 0.0;
                case TRAINING  -> 5.0;
                case CARGO     -> 2.0;
                case VIP       -> 8.0;
                case MEDICAL_EVAC -> 15.0;
                case COMBAT    -> 25.0;
            };

            // ── Emergency → inject anomalies ──
            if (scenario.getEmergencyType() != EmergencyType.NONE) {
                scenarioStressBonus += 30.0;
                scenarioFatigueMultiplier *= 1.3;

                switch (scenario.getEmergencyType()) {
                    case ENGINE_FAILURE -> {
                        airspeed *= 0.75;
                        verticalSpeed -= 500;
                    }
                    case HYDRAULIC_LOSS -> {
                        roll += RANDOM.nextGaussian() * 5;
                        pitch += RANDOM.nextGaussian() * 3;
                    }
                    case FIRE, FUEL_LEAK -> {
                        scenarioStressBonus += 20.0;
                    }
                    case CABIN_DEPRESSURIZATION -> {
                        altitude = Math.min(altitude, 10000); // emergency descent
                        verticalSpeed = Math.min(verticalSpeed, -3000);
                    }
                    case BIRD_STRIKE -> {
                        turbulenceLevel = Math.min(1.0, turbulenceLevel + 0.25);
                    }
                    case ELECTRICAL_FAILURE -> {
                        autopilotEngaged = false;
                        scenarioStressBonus += 15.0;
                    }
                    case GEAR_MALFUNCTION -> {
                        if (phaseOfFlight == PhaseOfFlight.LANDING) {
                            scenarioStressBonus += 35.0;
                        }
                    }
                    default -> { /* NONE handled above */ }
                }
            }

            // ── High altitude airport → thinner air = performance penalty ──
            if (scenario.getAirportElevationFt() > 3000) {
                double altPenalty = (scenario.getAirportElevationFt() - 3000) / 10000.0;
                airspeed *= (1.0 - altPenalty * 0.1);
                verticalSpeed *= (1.0 - altPenalty * 0.05);
            }

            log.debug("Scenario modifiers applied: stressBonus={}, fatigueMultiplier={}, turbulence={}, weather={}",
                    String.format("%.1f", scenarioStressBonus),
                    String.format("%.2f", scenarioFatigueMultiplier),
                    String.format("%.3f", turbulenceLevel),
                    scenario.getWeatherCondition());
        }

        // Pilot specific logic
        int baseReactionTimeMs = 300;
        double baseControlJitter = 0.1;
        double baseChecklistDelay = 2.0;
        double currentStress = pilot.getBaselineStressSensitivity() + (turbulenceLevel * 20) + scenarioStressBonus;
        double fatigueAccumulationRate = pilot.getBaselineFatigueRate() * scenarioFatigueMultiplier;

        switch (pilot.getProfileType()) {
            case NOVICE:
                baseReactionTimeMs += 150;
                baseControlJitter += 0.2;
                baseChecklistDelay += 3.0;
                break;
            case EXPERIENCED:
                baseReactionTimeMs -= 50;
                baseControlJitter -= 0.05;
                baseChecklistDelay -= 1.0;
                currentStress *= 0.8;
                break;
            case FATIGUE_PRONE:
                fatigueAccumulationRate *= 1.5;
                break;
            case HIGH_STRESS:
                currentStress *= 1.5;
                break;
        }

        // Add noise to pilot metrics
        int reactionTimeMs = Math.max(150, (int) (baseReactionTimeMs + RANDOM.nextGaussian() * 50));
        double controlJitterIndex = Math.max(0, baseControlJitter + RANDOM.nextGaussian() * 0.05);
        double checklistDelaySeconds = Math.max(0, baseChecklistDelay + RANDOM.nextGaussian() * 1.0);

        double stressIndex = Math.max(0, Math.min(100, currentStress + RANDOM.nextGaussian() * 5));

        // Accumulate fatigue
        double fatigueIndex = Math.min(100,
                (currentSimulationTimeSeconds / 60.0) * fatigueAccumulationRate + (stressIndex * 0.1));

        double heartRate = Math.max(60, 70 + (stressIndex * 0.5) + (fatigueIndex * 0.2) + RANDOM.nextGaussian() * 2);
        double blinkRate = Math.max(10, 20 - (fatigueIndex * 0.1) + RANDOM.nextGaussian() * 1);

        double controlInputFrequency = autopilotEngaged ? 0.5 : 2.0 + RANDOM.nextGaussian() * 0.5;
        double taskSwitchRate = 1.0 + Math.abs(RANDOM.nextGaussian() * 0.2);
        // Error rate increases under scenario stress
        double errorThreshold = (scenario != null && scenario.getEmergencyType() != EmergencyType.NONE) ? 0.80
                : (scenario != null && scenario.getDifficultyPreset() == DifficultyPreset.EXTREME) ? 0.85 : 0.95;
        int errorCount = RANDOM.nextDouble() > errorThreshold ? 1 : 0;
        double instrumentScanVariance = autopilotEngaged ? 0.8 : 0.4 + RANDOM.nextGaussian() * 0.1;

        // Deviations
        double altitudeDeviation = Math.abs(altitude - baseAltitude);
        double verticalSpeedInstability = Math.abs(verticalSpeed - baseVerticalSpeed);
        double airspeedDeviation = Math.abs(airspeed - baseAirspeed);
        double headingDeviation = Math.abs(heading - baseHeading);
        double pitchInstability = Math.abs(pitch - (baseVerticalSpeed / 100.0));
        double rollInstability = Math.abs(roll);

        TelemetryFrame frame = TelemetryFrame.builder()
                .flightSession(session)
                .frameNumber(nextFrameNumber)
                .timestamp(Instant.now())
                .phaseOfFlight(phaseOfFlight)
                .altitude(altitude)
                .airspeed(airspeed)
                .verticalSpeed(verticalSpeed)
                .heading(heading)
                .pitch(pitch)
                .roll(roll)
                .yawRate(yawRate)
                .turbulenceLevel(turbulenceLevel)
                .weatherSeverity(weatherSeverity)
                .autopilotEngaged(autopilotEngaged)
                .reactionTimeMs(reactionTimeMs)
                .controlInputFrequency(controlInputFrequency)
                .checklistDelaySeconds(checklistDelaySeconds)
                .taskSwitchRate(taskSwitchRate)
                .errorCount(errorCount)
                .controlJitterIndex(controlJitterIndex)
                .instrumentScanVariance(instrumentScanVariance)
                .heartRate(heartRate)
                .blinkRate(blinkRate)
                .fatigueIndex(fatigueIndex)
                .stressIndex(stressIndex)
                .altitudeDeviation(altitudeDeviation)
                .verticalSpeedInstability(verticalSpeedInstability)
                .airspeedDeviation(airspeedDeviation)
                .headingDeviation(headingDeviation)
                .pitchInstability(pitchInstability)
                .rollInstability(rollInstability)
                .build();

        telemetryFrameRepository.save(frame);

        session.setTotalFramesGenerated(nextFrameNumber);
        flightSessionRepository.save(session);

        log.info("Frame {} successfully generated and saved. Phase: {}, Alt: {}, Fatigue: {}",
                nextFrameNumber, phaseOfFlight, Math.round(altitude), String.format("%.1f", fatigueIndex));
    }

    private PhaseOfFlight determinePhaseOfFlight(int timeSeconds) {
        if (timeSeconds <= 300) {
            return PhaseOfFlight.TAKEOFF;
        } else if (timeSeconds <= 600) {
            return PhaseOfFlight.CLIMB;
        } else if (timeSeconds <= 1800) {
            return PhaseOfFlight.CRUISE;
        } else if (timeSeconds <= 2400) {
            return PhaseOfFlight.DESCENT;
        } else if (timeSeconds <= 2700) {
            return PhaseOfFlight.APPROACH;
        } else {
            return PhaseOfFlight.LANDING;
        }
    }
}

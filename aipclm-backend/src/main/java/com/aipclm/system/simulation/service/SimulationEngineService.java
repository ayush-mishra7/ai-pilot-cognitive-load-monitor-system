package com.aipclm.system.simulation.service;

import com.aipclm.system.adsb.service.AdsbService;
import com.aipclm.system.crm.model.CrewAssignment;
import com.aipclm.system.crm.repository.CrewAssignmentRepository;
import com.aipclm.system.crm.service.CrmService;
import com.aipclm.system.pilot.model.CrewRole;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.pilot.repository.PilotRepository;
import com.aipclm.system.scenario.model.*;
import com.aipclm.system.scenario.repository.FlightScenarioRepository;
import com.aipclm.system.sensor.model.SensorType;
import com.aipclm.system.sensor.service.SensorIngestionService;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.model.FlightSessionStatus;
import com.aipclm.system.session.repository.FlightSessionRepository;
import com.aipclm.system.telemetry.model.PhaseOfFlight;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import com.aipclm.system.telemetry.repository.TelemetryFrameRepository;
import com.aipclm.system.weather.model.WeatherObservation;
import com.aipclm.system.weather.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class SimulationEngineService {

    private final FlightSessionRepository flightSessionRepository;
    private final PilotRepository pilotRepository;
    private final TelemetryFrameRepository telemetryFrameRepository;
    private final FlightScenarioRepository scenarioRepository;
    private final CrewAssignmentRepository crewAssignmentRepository;
    private final CrmService crmService;
    private final SensorIngestionService sensorIngestionService;
    private final WeatherService weatherService;
    private final AdsbService adsbService;

    private static final java.util.Random RANDOM = new java.util.Random(42); // Seeded for determinism

    /** ICAO coordinates for common airports (used for ADS-B reference) */
    private static final Map<String, double[]> AIRPORT_COORDS = Map.ofEntries(
            Map.entry("KJFK", new double[]{40.6413, -73.7781}),
            Map.entry("KLAX", new double[]{33.9425, -118.4081}),
            Map.entry("KORD", new double[]{41.9742, -87.9073}),
            Map.entry("KATL", new double[]{33.6407, -84.4277}),
            Map.entry("EGLL", new double[]{51.4700, -0.4543}),
            Map.entry("LFPG", new double[]{49.0097, 2.5479}),
            Map.entry("RJTT", new double[]{35.5494, 139.7798}),
            Map.entry("VHHH", new double[]{22.3080, 113.9185}),
            Map.entry("OMDB", new double[]{25.2528, 55.3644}),
            Map.entry("KSFO", new double[]{37.6213, -122.3790})
    );

    public SimulationEngineService(FlightSessionRepository flightSessionRepository,
            PilotRepository pilotRepository,
            TelemetryFrameRepository telemetryFrameRepository,
            FlightScenarioRepository scenarioRepository,
            CrewAssignmentRepository crewAssignmentRepository,
            CrmService crmService,
            SensorIngestionService sensorIngestionService,
            WeatherService weatherService,
            AdsbService adsbService) {
        this.flightSessionRepository = flightSessionRepository;
        this.pilotRepository = pilotRepository;
        this.telemetryFrameRepository = telemetryFrameRepository;
        this.scenarioRepository = scenarioRepository;
        this.crewAssignmentRepository = crewAssignmentRepository;
        this.crmService = crmService;
        this.sensorIngestionService = sensorIngestionService;
        this.weatherService = weatherService;
        this.adsbService = adsbService;
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

        // ── Phase 8: Dynamic Weather injection ──
        Double windShearIndex = null;
        Double icingLevel = null;
        Double ceilingFt = null;
        Double visibilityNm = null;
        if (session.getIcaoAirport() != null && !session.getIcaoAirport().isBlank()) {
            try {
                WeatherObservation wx = weatherService.getLatestCached(session.getIcaoAirport()).orElse(null);
                if (wx == null) {
                    wx = weatherService.fetchMetar(session.getIcaoAirport());
                }
                if (wx != null) {
                    weatherSeverity = wx.getWeatherSeverityScore();
                    windShearIndex = wx.isWindShearReported() ? 0.7 + RANDOM.nextGaussian() * 0.1 : RANDOM.nextDouble() * 0.15;
                    icingLevel = wx.isIcingReported() ? 0.6 + RANDOM.nextGaussian() * 0.1 : 0.0;
                    ceilingFt = wx.getCeilingFt() != null ? (double) wx.getCeilingFt() : null;
                    visibilityNm = wx.getVisibilitySm() != null ? wx.getVisibilitySm() : null;
                    // Weather-driven stress boost
                    scenarioStressBonus += weatherSeverity * 20;
                    turbulenceLevel = Math.min(1.0, turbulenceLevel + weatherSeverity * 0.3);
                    log.debug("Dynamic weather applied: icao={} severity={} ceiling={} vis={}",
                            session.getIcaoAirport(), wx.getWeatherSeverityScore(), ceilingFt, visibilityNm);
                }
            } catch (Exception e) {
                log.warn("Weather injection failed for {}: {}", session.getIcaoAirport(), e.getMessage());
            }
        }

        // ── Phase 8: ADS-B Traffic injection ──
        Integer nearbyAircraftCount = null;
        Double closestAircraftDistanceNm = null;
        boolean tcasAdvisoryActive = false;
        if (session.isAdsbMode()) {
            try {
                double[] coords = resolveAirportCoords(session.getIcaoAirport());
                if (coords != null) {
                    AdsbService.TrafficSummary traffic = adsbService.getTrafficSummary(coords[0], coords[1]);
                    nearbyAircraftCount = traffic.totalAircraft();
                    closestAircraftDistanceNm = traffic.closestDistanceNm();
                    tcasAdvisoryActive = traffic.closestDistanceNm() != null && traffic.closestDistanceNm() < 2.0;
                    // Traffic density stress
                    if (traffic.withinFiveNm() > 3) {
                        scenarioStressBonus += traffic.withinFiveNm() * 2.0;
                    }
                    log.debug("ADS-B traffic: {} nearby, closest={}nm, TCAS={}",
                            nearbyAircraftCount, closestAircraftDistanceNm, tcasAdvisoryActive);
                }
            } catch (Exception e) {
                log.warn("ADS-B injection failed: {}", e.getMessage());
            }
        }

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
                // Phase 8: Weather & ADS-B
                .windShearIndex(windShearIndex)
                .icingLevel(icingLevel)
                .ceilingFt(ceilingFt)
                .visibilityNm(visibilityNm)
                .nearbyAircraftCount(nearbyAircraftCount)
                .closestAircraftDistanceNm(closestAircraftDistanceNm)
                .tcasAdvisoryActive(tcasAdvisoryActive)
                .build();

        applySensorOverrides(frame, sessionId);

        telemetryFrameRepository.save(frame);

        session.setTotalFramesGenerated(nextFrameNumber);
        flightSessionRepository.save(session);

        log.info("Frame {} successfully generated and saved. Phase: {}, Alt: {}, Fatigue: {}{}",
                nextFrameNumber, phaseOfFlight, Math.round(altitude), String.format("%.1f", fatigueIndex),
                frame.isSensorOverride() ? " [SENSOR]" : "");
    }

    // ───────────────────── CREW MODE FRAME GENERATION ─────────────────────

    /**
     * Generates two telemetry frames (Captain + First Officer) for a crew-mode session.
     * Both frames share the same cockpit state (altitude, airspeed, etc.) but have
     * individual pilot biometrics and cross-crew stress/fatigue propagation.
     *
     * @param sessionId the crew-mode flight session
     */
    @Transactional
    public void generateCrewFrames(UUID sessionId) {
        log.info("Generating crew telemetry frames for session: {}", sessionId);

        FlightSession session = flightSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() != FlightSessionStatus.RUNNING) {
            log.warn("Cannot generate crew frames for session {}; status is {}", sessionId, session.getStatus());
            return;
        }

        // Determine next frame number (use Captain frame as reference)
        int nextFrameNumber = session.getTotalFramesGenerated() + 1;
        var latestOpt = telemetryFrameRepository
                .findTopByFlightSessionIdAndCrewRoleOrderByFrameNumberDesc(sessionId, CrewRole.CAPTAIN);
        if (latestOpt.isPresent() && latestOpt.get().getFrameNumber() >= nextFrameNumber) {
            log.warn("Duplicate crew frame detected for session {} frame {}", sessionId, nextFrameNumber);
            return;
        }

        // Load crew assignments
        List<CrewAssignment> assignments = crewAssignmentRepository.findByFlightSessionId(sessionId);
        CrewAssignment captainAssignment = assignments.stream()
                .filter(a -> a.getCrewRole() == CrewRole.CAPTAIN).findFirst()
                .orElseThrow(() -> new IllegalStateException("No CAPTAIN assigned for session " + sessionId));
        CrewAssignment foAssignment = assignments.stream()
                .filter(a -> a.getCrewRole() == CrewRole.FIRST_OFFICER).findFirst()
                .orElseThrow(() -> new IllegalStateException("No FIRST_OFFICER assigned for session " + sessionId));

        Pilot captainPilot = pilotRepository.findById(captainAssignment.getPilot().getId()).orElseThrow();
        Pilot foPilot = pilotRepository.findById(foAssignment.getPilot().getId()).orElseThrow();

        // Load scenario
        FlightScenario scenario = scenarioRepository.findByFlightSessionId(sessionId).orElse(null);

        // ── Shared cockpit state (computed once, shared by both frames) ──
        int currentSimulationTimeSeconds = nextFrameNumber * session.getFrameFrequencySeconds();
        PhaseOfFlight phaseOfFlight = determinePhaseOfFlight(currentSimulationTimeSeconds);

        SharedCockpitState cockpit = generateSharedCockpitState(phaseOfFlight, scenario);

        // ── Cross-crew propagation ──
        double[] captainPropagation = crmService.computeCrossCrewPropagation(sessionId, CrewRole.CAPTAIN);
        double[] foPropagation = crmService.computeCrossCrewPropagation(sessionId, CrewRole.FIRST_OFFICER);

        // ── Generate individual pilot frames ──
        boolean captainIsFlying = captainAssignment.isPilotFlying();

        TelemetryFrame captainFrame = buildCrewFrame(session, nextFrameNumber, phaseOfFlight, cockpit,
                captainPilot, CrewRole.CAPTAIN, captainIsFlying, scenario,
                currentSimulationTimeSeconds, captainPropagation);

        TelemetryFrame foFrame = buildCrewFrame(session, nextFrameNumber, phaseOfFlight, cockpit,
                foPilot, CrewRole.FIRST_OFFICER, !captainIsFlying, scenario,
                currentSimulationTimeSeconds, foPropagation);

        applySensorOverrides(captainFrame, sessionId);
        applySensorOverrides(foFrame, sessionId);

        telemetryFrameRepository.save(captainFrame);
        telemetryFrameRepository.save(foFrame);

        session.setTotalFramesGenerated(nextFrameNumber);
        flightSessionRepository.save(session);

        log.info("Crew frames {} generated. Captain fatigue={} FO fatigue={} Phase={}{}",
                nextFrameNumber,
                String.format("%.1f", captainFrame.getFatigueIndex()),
                String.format("%.1f", foFrame.getFatigueIndex()),
                phaseOfFlight,
                captainFrame.isSensorOverride() ? " [SENSOR]" : "");
    }

    /**
     * Shared cockpit state record — environmental values common to both crew members.
     */
    private record SharedCockpitState(
            double altitude, double airspeed, double verticalSpeed,
            double heading, double pitch, double roll, double yawRate,
            double turbulenceLevel, double weatherSeverity, boolean autopilotEngaged,
            double baseAltitude, double baseAirspeed, double baseVerticalSpeed, double baseHeading,
            double scenarioStressBonus, double scenarioFatigueMultiplier) {}

    /**
     * Generates the shared cockpit state (altitude, airspeed, weather, etc.)
     * using the same logic as generateNextFrame but returns the values rather
     * than building a full frame.
     */
    private SharedCockpitState generateSharedCockpitState(PhaseOfFlight phaseOfFlight,
                                                           FlightScenario scenario) {
        // ── Baseline values per phase ──
        double baseAltitude, baseAirspeed, baseVerticalSpeed, baseHeading;
        switch (phaseOfFlight) {
            case TAKEOFF -> { baseAltitude = 1500; baseAirspeed = 160; baseVerticalSpeed = 2000; baseHeading = 270; }
            case CLIMB -> { baseAltitude = 15000; baseAirspeed = 250; baseVerticalSpeed = 1500; baseHeading = 270; }
            case CRUISE -> { baseAltitude = 35000; baseAirspeed = 450; baseVerticalSpeed = 0; baseHeading = 270; }
            case DESCENT -> { baseAltitude = 15000; baseAirspeed = 300; baseVerticalSpeed = -1500; baseHeading = 270; }
            case APPROACH -> { baseAltitude = 3000; baseAirspeed = 180; baseVerticalSpeed = -800; baseHeading = 270; }
            case LANDING -> { baseAltitude = 500; baseAirspeed = 140; baseVerticalSpeed = -500; baseHeading = 270; }
            default -> { baseAltitude = 10000; baseAirspeed = 250; baseVerticalSpeed = 0; baseHeading = 270; }
        }

        double altitude = baseAltitude + RANDOM.nextGaussian() * 200;
        double airspeed = baseAirspeed + RANDOM.nextGaussian() * 10;
        double verticalSpeed = baseVerticalSpeed + RANDOM.nextGaussian() * 100;
        double heading = (baseHeading + RANDOM.nextGaussian() * 2) % 360;
        double pitch = (baseVerticalSpeed / 100.0) + RANDOM.nextGaussian() * 1.5;
        double roll = RANDOM.nextGaussian() * 3;
        double yawRate = RANDOM.nextGaussian() * 0.5;
        double turbulenceLevel = Math.max(0, Math.min(1, 0.1 + RANDOM.nextGaussian() * 0.05));
        double weatherSeverity = Math.max(0, Math.min(1, 0.2 + RANDOM.nextGaussian() * 0.1));
        boolean autopilotEngaged = (phaseOfFlight != PhaseOfFlight.TAKEOFF && phaseOfFlight != PhaseOfFlight.LANDING);

        double scenarioStressBonus = 0.0;
        double scenarioFatigueMultiplier = 1.0;

        if (scenario != null) {
            // Apply same scenario modifiers as single-pilot mode (mirrors generateNextFrame)

            // ── Weather → turbulence & weatherSeverity ──
            double turbulenceMultiplier = switch (scenario.getWeatherCondition()) {
                case THUNDERSTORM -> 3.0; case SNOW -> 2.2; case ICE -> 2.0;
                case RAIN -> 1.5; case OVERCAST -> 1.2; case FOG -> 1.0;
                case CLOUDY -> 1.1; case CLEAR -> 1.0;
            };
            turbulenceLevel = Math.min(1.0, turbulenceLevel * turbulenceMultiplier);

            weatherSeverity = switch (scenario.getWeatherCondition()) {
                case CLEAR -> 0.05; case CLOUDY -> 0.15; case OVERCAST -> 0.25;
                case RAIN -> 0.45; case FOG -> 0.40; case SNOW -> 0.55;
                case ICE -> 0.70; case THUNDERSTORM -> 0.90;
            };

            // ── Visibility → stress bonus ──
            scenarioStressBonus += switch (scenario.getVisibility()) {
                case UNLIMITED -> 0.0; case GOOD -> 2.0; case MODERATE -> 8.0;
                case LOW -> 20.0; case VERY_LOW -> 30.0; case ZERO -> 40.0;
            };

            // ── Time of Day → stress & fatigue ──
            if (scenario.getTimeOfDay() == TimeOfDay.NIGHT) {
                scenarioStressBonus += 15.0;
                scenarioFatigueMultiplier *= 1.15;
            } else if (scenario.getTimeOfDay() == TimeOfDay.DUSK) {
                scenarioStressBonus += 8.0;
                scenarioFatigueMultiplier *= 1.05;
            }

            // ── Wind → heading deviation ──
            double windEffect = scenario.getWindSpeedKnots() / 80.0;
            heading = (heading + scenario.getCrosswindComponent() * 0.3 * RANDOM.nextGaussian()) % 360;
            roll += scenario.getCrosswindComponent() * 0.1 * RANDOM.nextGaussian();
            yawRate += windEffect * RANDOM.nextGaussian() * 1.5;

            // ── Terrain → turbulence ──
            if (scenario.getTerrainType() == TerrainType.MOUNTAINOUS) {
                turbulenceLevel = Math.min(1.0, turbulenceLevel + 0.15);
                altitude = Math.max(altitude, scenario.getAirportElevationFt() + 1000);
            } else if (scenario.getTerrainType() == TerrainType.URBAN) {
                turbulenceLevel = Math.min(1.0, turbulenceLevel + 0.05);
            }

            // ── Runway → landing stress ──
            if ((phaseOfFlight == PhaseOfFlight.APPROACH || phaseOfFlight == PhaseOfFlight.LANDING)
                    && scenario.getRunwayCondition() != RunwayCondition.DRY) {
                double runwayPenalty = switch (scenario.getRunwayCondition()) {
                    case WET -> 0.10; case CONTAMINATED -> 0.20; case ICY -> 0.30;
                    case FLOODED -> 0.35; default -> 0.0;
                };
                scenarioStressBonus += runwayPenalty * 50;
                airspeed += runwayPenalty * 10;
            }

            // ── Mission type → base stress ──
            scenarioStressBonus += switch (scenario.getMissionType()) {
                case ROUTINE -> 0.0; case TRAINING -> 5.0; case CARGO -> 2.0;
                case VIP -> 8.0; case MEDICAL_EVAC -> 15.0; case COMBAT -> 25.0;
            };

            if (scenario.getEmergencyType() != EmergencyType.NONE) {
                scenarioStressBonus += 30.0;
                scenarioFatigueMultiplier *= 1.3;
                switch (scenario.getEmergencyType()) {
                    case ENGINE_FAILURE -> { airspeed *= 0.75; verticalSpeed -= 500; }
                    case HYDRAULIC_LOSS -> { roll += RANDOM.nextGaussian() * 5; pitch += RANDOM.nextGaussian() * 3; }
                    case FIRE, FUEL_LEAK -> scenarioStressBonus += 20.0;
                    case CABIN_DEPRESSURIZATION -> { altitude = Math.min(altitude, 10000); verticalSpeed = Math.min(verticalSpeed, -3000); }
                    case BIRD_STRIKE -> turbulenceLevel = Math.min(1.0, turbulenceLevel + 0.25);
                    case ELECTRICAL_FAILURE -> { autopilotEngaged = false; scenarioStressBonus += 15.0; }
                    case GEAR_MALFUNCTION -> { if (phaseOfFlight == PhaseOfFlight.LANDING) scenarioStressBonus += 35.0; }
                    default -> { /* NONE handled above */ }
                }
            }

            if (scenario.getAirportElevationFt() > 3000) {
                double altPenalty = (scenario.getAirportElevationFt() - 3000) / 10000.0;
                airspeed *= (1.0 - altPenalty * 0.1);
                verticalSpeed *= (1.0 - altPenalty * 0.05);
            }
        }

        return new SharedCockpitState(altitude, airspeed, verticalSpeed, heading, pitch, roll, yawRate,
                turbulenceLevel, weatherSeverity, autopilotEngaged,
                baseAltitude, baseAirspeed, baseVerticalSpeed, baseHeading,
                scenarioStressBonus, scenarioFatigueMultiplier);
    }

    /**
     * Builds a TelemetryFrame for one crew member using shared cockpit state
     * and individual pilot biometrics.
     */
    private TelemetryFrame buildCrewFrame(FlightSession session, int frameNumber,
                                           PhaseOfFlight phaseOfFlight, SharedCockpitState cockpit,
                                           Pilot pilot, CrewRole crewRole, boolean isFlying,
                                           FlightScenario scenario,
                                           int currentSimulationTimeSeconds, double[] crossCrewPropagation) {

        double scenarioStressBonus = cockpit.scenarioStressBonus();
        double scenarioFatigueMultiplier = cockpit.scenarioFatigueMultiplier();

        // ── Pilot-specific metrics ──
        int baseReactionTimeMs = 300;
        double baseControlJitter = 0.1;
        double baseChecklistDelay = 2.0;
        double currentStress = pilot.getBaselineStressSensitivity()
                + (cockpit.turbulenceLevel() * 20) + scenarioStressBonus;
        double fatigueAccumulationRate = pilot.getBaselineFatigueRate() * scenarioFatigueMultiplier;

        switch (pilot.getProfileType()) {
            case NOVICE -> {
                baseReactionTimeMs += 150;
                baseControlJitter += 0.2;
                baseChecklistDelay += 3.0;
            }
            case EXPERIENCED -> {
                baseReactionTimeMs -= 50;
                baseControlJitter -= 0.05;
                baseChecklistDelay -= 1.0;
                currentStress *= 0.8;
            }
            case FATIGUE_PRONE -> fatigueAccumulationRate *= 1.5;
            case HIGH_STRESS -> currentStress *= 1.5;
        }

        // ── Cross-crew propagation ──
        currentStress += crossCrewPropagation[0];
        fatigueAccumulationRate += crossCrewPropagation[1] * 0.01; // subtle fatigue influence

        // ── Pilot Flying vs Monitoring role adjustments ──
        if (!isFlying) {
            // Pilot Monitoring has lower control input, higher instrument scan
            baseReactionTimeMs += 50;  // less time-critical
            baseControlJitter *= 0.5;  // not actively flying
            baseChecklistDelay -= 0.5; // better at monitoring checklists
        }

        int reactionTimeMs = Math.max(150, (int) (baseReactionTimeMs + RANDOM.nextGaussian() * 50));
        double controlJitterIndex = Math.max(0, baseControlJitter + RANDOM.nextGaussian() * 0.05);
        double checklistDelaySeconds = Math.max(0, baseChecklistDelay + RANDOM.nextGaussian() * 1.0);
        double stressIndex = Math.max(0, Math.min(100, currentStress + RANDOM.nextGaussian() * 5));
        double fatigueIndex = Math.min(100,
                (currentSimulationTimeSeconds / 60.0) * fatigueAccumulationRate + (stressIndex * 0.1));
        double heartRate = Math.max(60, 70 + (stressIndex * 0.5) + (fatigueIndex * 0.2) + RANDOM.nextGaussian() * 2);
        double blinkRate = Math.max(10, 20 - (fatigueIndex * 0.1) + RANDOM.nextGaussian() * 1);

        double controlInputFrequency = isFlying
                ? (cockpit.autopilotEngaged() ? 0.5 : 2.0 + RANDOM.nextGaussian() * 0.5)
                : 0.3 + RANDOM.nextGaussian() * 0.1; // PM makes few control inputs
        double taskSwitchRate = 1.0 + Math.abs(RANDOM.nextGaussian() * 0.2);

        double errorThreshold = (scenario != null && scenario.getEmergencyType() != EmergencyType.NONE) ? 0.80
                : (scenario != null && scenario.getDifficultyPreset() == DifficultyPreset.EXTREME) ? 0.85 : 0.95;
        int errorCount = RANDOM.nextDouble() > errorThreshold ? 1 : 0;
        double instrumentScanVariance = isFlying
                ? (cockpit.autopilotEngaged() ? 0.8 : 0.4 + RANDOM.nextGaussian() * 0.1)
                : 0.6 + RANDOM.nextGaussian() * 0.15; // PM scans instruments more broadly

        // ── Deviations (PF-driven, PM has minimal deviations) ──
        double deviationScale = isFlying ? 1.0 : 0.2;
        double altitudeDeviation = Math.abs(cockpit.altitude() - cockpit.baseAltitude()) * deviationScale;
        double verticalSpeedInstability = Math.abs(cockpit.verticalSpeed() - cockpit.baseVerticalSpeed()) * deviationScale;
        double airspeedDeviation = Math.abs(cockpit.airspeed() - cockpit.baseAirspeed()) * deviationScale;
        double headingDeviation = Math.abs(cockpit.heading() - cockpit.baseHeading()) * deviationScale;
        double pitchInstability = Math.abs(cockpit.pitch() - (cockpit.baseVerticalSpeed() / 100.0)) * deviationScale;
        double rollInstability = Math.abs(cockpit.roll()) * deviationScale;

        return TelemetryFrame.builder()
                .flightSession(session)
                .frameNumber(frameNumber)
                .timestamp(Instant.now())
                .phaseOfFlight(phaseOfFlight)
                .crewRole(crewRole)
                .altitude(cockpit.altitude())
                .airspeed(cockpit.airspeed())
                .verticalSpeed(cockpit.verticalSpeed())
                .heading(cockpit.heading())
                .pitch(cockpit.pitch())
                .roll(cockpit.roll())
                .yawRate(cockpit.yawRate())
                .turbulenceLevel(cockpit.turbulenceLevel())
                .weatherSeverity(cockpit.weatherSeverity())
                .autopilotEngaged(cockpit.autopilotEngaged())
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
                .windShearIndex(0.0)
                .icingLevel(0.0)
                .ceilingFt(12000.0)
                .visibilityNm(10.0)
                .nearbyAircraftCount(0)
                .closestAircraftDistanceNm(99.0)
                .tcasAdvisoryActive(false)
                .build();
    }

    // ───────────────────── SENSOR OVERRIDE ─────────────────────

    /**
     * If the session has connected wearable sensors, override the simulated
     * biometric values with real sensor readings.
     */
    private void applySensorOverrides(TelemetryFrame frame, UUID sessionId) {
        try {
            Map<SensorType, Double> sensorValues = sensorIngestionService.getLatestSensorValues(sessionId);
            if (sensorValues.isEmpty()) return;

            boolean overridden = false;

            if (sensorValues.containsKey(SensorType.HEART_RATE_MONITOR)) {
                frame.setHeartRate(sensorValues.get(SensorType.HEART_RATE_MONITOR));
                overridden = true;
            }
            if (sensorValues.containsKey(SensorType.EEG_HEADBAND)) {
                double eegVal = sensorValues.get(SensorType.EEG_HEADBAND);
                // Split into alpha/beta/theta approximation bands
                frame.setEegAlphaPower(eegVal * 0.40);
                frame.setEegBetaPower(eegVal * 0.35);
                frame.setEegThetaPower(eegVal * 0.25);
                overridden = true;
            }
            if (sensorValues.containsKey(SensorType.EYE_TRACKER)) {
                double eyeVal = sensorValues.get(SensorType.EYE_TRACKER);
                frame.setPupilDiameter(eyeVal);
                frame.setGazeFixationDurationMs(200.0 + eyeVal * 30);
                frame.setBlinkRate(Math.max(5, 20 - eyeVal * 0.8));
                overridden = true;
            }
            if (sensorValues.containsKey(SensorType.GSR_SENSOR)) {
                frame.setGsrLevel(sensorValues.get(SensorType.GSR_SENSOR));
                overridden = true;
            }
            if (sensorValues.containsKey(SensorType.PULSE_OXIMETER)) {
                frame.setSpO2Level(sensorValues.get(SensorType.PULSE_OXIMETER));
                overridden = true;
            }
            if (sensorValues.containsKey(SensorType.SKIN_TEMPERATURE_SENSOR)) {
                frame.setSkinTemperature(sensorValues.get(SensorType.SKIN_TEMPERATURE_SENSOR));
                overridden = true;
            }

            if (overridden) {
                frame.setSensorOverride(true);
            }
        } catch (Exception e) {
            log.warn("Sensor override failed for session {}: {}", sessionId, e.getMessage());
        }
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

    /**
     * Resolve airport ICAO code to lat/lon coordinates for ADS-B queries.
     * Returns null if the airport is not in our lookup table.
     */
    private double[] resolveAirportCoords(String icaoAirport) {
        if (icaoAirport == null || icaoAirport.isBlank()) return null;
        return AIRPORT_COORDS.getOrDefault(icaoAirport.toUpperCase(), null);
    }
}

package com.aipclm.system.scenario.service;

import com.aipclm.system.scenario.dto.ScenarioRequest;
import com.aipclm.system.scenario.model.*;
import com.aipclm.system.scenario.repository.FlightScenarioRepository;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.repository.FlightSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScenarioService {

    private final FlightScenarioRepository scenarioRepository;
    private final FlightSessionRepository sessionRepository;

    /**
     * Create a scenario for a flight session. If the request is null or has null fields,
     * defaults are applied (NORMAL difficulty, CLEAR weather, etc.).
     */
    @Transactional
    public FlightScenario createScenario(UUID sessionId, ScenarioRequest req) {
        FlightSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (scenarioRepository.existsByFlightSessionId(sessionId)) {
            throw new IllegalStateException("Scenario already exists for session: " + sessionId);
        }

        FlightScenario scenario;

        if (req != null && req.getDifficultyPreset() != null && req.getDifficultyPreset() != DifficultyPreset.NORMAL) {
            // Apply a preset and then override individual fields if provided
            scenario = buildFromPreset(req.getDifficultyPreset(), session);
            applyOverrides(scenario, req);
        } else if (req != null) {
            scenario = buildFromRequest(req, session);
        } else {
            scenario = FlightScenario.builder().flightSession(session).build();
        }

        scenario = scenarioRepository.save(scenario);
        log.info("Created scenario for session {} — preset={}, weather={}, emergency={}",
                sessionId, scenario.getDifficultyPreset(), scenario.getWeatherCondition(), scenario.getEmergencyType());
        return scenario;
    }

    /**
     * Retrieve the scenario for a given session.
     */
    public FlightScenario getScenario(UUID sessionId) {
        return scenarioRepository.findByFlightSessionId(sessionId)
                .orElse(null);
    }

    /**
     * Update scenario mid-flight (e.g., inject emergency).
     */
    @Transactional
    public FlightScenario updateScenario(UUID sessionId, ScenarioRequest req) {
        FlightScenario scenario = scenarioRepository.findByFlightSessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("No scenario found for session: " + sessionId));

        applyOverrides(scenario, req);
        scenario = scenarioRepository.save(scenario);
        log.info("Updated scenario for session {} — weather={}, emergency={}, difficulty={}",
                sessionId, scenario.getWeatherCondition(), scenario.getEmergencyType(), scenario.getDifficultyPreset());
        return scenario;
    }

    /* ─── Preset builders ─── */

    private FlightScenario buildFromPreset(DifficultyPreset preset, FlightSession session) {
        return switch (preset) {
            case MODERATE -> FlightScenario.builder()
                    .flightSession(session)
                    .difficultyPreset(DifficultyPreset.MODERATE)
                    .weatherCondition(WeatherCondition.RAIN)
                    .visibility(VisibilityLevel.MODERATE)
                    .windSpeedKnots(25)
                    .windGustKnots(15)
                    .crosswindComponent(12.0)
                    .temperatureC(8)
                    .timeOfDay(TimeOfDay.DUSK)
                    .moonIllumination(0.4)
                    .terrainType(TerrainType.COASTAL)
                    .runwayCondition(RunwayCondition.WET)
                    .runwayLengthFt(8000)
                    .airportElevationFt(1200)
                    .missionType(MissionType.ROUTINE)
                    .emergencyType(EmergencyType.NONE)
                    .build();
            case EXTREME -> FlightScenario.builder()
                    .flightSession(session)
                    .difficultyPreset(DifficultyPreset.EXTREME)
                    .weatherCondition(WeatherCondition.THUNDERSTORM)
                    .visibility(VisibilityLevel.VERY_LOW)
                    .windSpeedKnots(55)
                    .windGustKnots(35)
                    .crosswindComponent(28.0)
                    .temperatureC(-5)
                    .timeOfDay(TimeOfDay.NIGHT)
                    .moonIllumination(0.1)
                    .terrainType(TerrainType.MOUNTAINOUS)
                    .runwayCondition(RunwayCondition.ICY)
                    .runwayLengthFt(6000)
                    .airportElevationFt(5400)
                    .missionType(MissionType.COMBAT)
                    .emergencyType(EmergencyType.ENGINE_FAILURE)
                    .build();
            default -> FlightScenario.builder()
                    .flightSession(session)
                    .difficultyPreset(DifficultyPreset.NORMAL)
                    .build();
        };
    }

    private FlightScenario buildFromRequest(ScenarioRequest req, FlightSession session) {
        return FlightScenario.builder()
                .flightSession(session)
                .weatherCondition(req.getWeatherCondition() != null ? req.getWeatherCondition() : WeatherCondition.CLEAR)
                .visibility(req.getVisibility() != null ? req.getVisibility() : VisibilityLevel.UNLIMITED)
                .windSpeedKnots(req.getWindSpeedKnots() != null ? req.getWindSpeedKnots() : 5)
                .windGustKnots(req.getWindGustKnots() != null ? req.getWindGustKnots() : 0)
                .crosswindComponent(req.getCrosswindComponent() != null ? req.getCrosswindComponent() : 0.0)
                .temperatureC(req.getTemperatureC() != null ? req.getTemperatureC() : 15)
                .timeOfDay(req.getTimeOfDay() != null ? req.getTimeOfDay() : TimeOfDay.DAY)
                .moonIllumination(req.getMoonIllumination() != null ? req.getMoonIllumination() : 0.0)
                .terrainType(req.getTerrainType() != null ? req.getTerrainType() : TerrainType.FLAT)
                .runwayCondition(req.getRunwayCondition() != null ? req.getRunwayCondition() : RunwayCondition.DRY)
                .runwayLengthFt(req.getRunwayLengthFt() != null ? req.getRunwayLengthFt() : 10000)
                .airportElevationFt(req.getAirportElevationFt() != null ? req.getAirportElevationFt() : 500)
                .missionType(req.getMissionType() != null ? req.getMissionType() : MissionType.ROUTINE)
                .emergencyType(req.getEmergencyType() != null ? req.getEmergencyType() : EmergencyType.NONE)
                .difficultyPreset(req.getDifficultyPreset() != null ? req.getDifficultyPreset() : DifficultyPreset.NORMAL)
                .build();
    }

    private void applyOverrides(FlightScenario scenario, ScenarioRequest req) {
        if (req == null) return;
        if (req.getWeatherCondition() != null) scenario.setWeatherCondition(req.getWeatherCondition());
        if (req.getVisibility() != null) scenario.setVisibility(req.getVisibility());
        if (req.getWindSpeedKnots() != null) scenario.setWindSpeedKnots(req.getWindSpeedKnots());
        if (req.getWindGustKnots() != null) scenario.setWindGustKnots(req.getWindGustKnots());
        if (req.getCrosswindComponent() != null) scenario.setCrosswindComponent(req.getCrosswindComponent());
        if (req.getTemperatureC() != null) scenario.setTemperatureC(req.getTemperatureC());
        if (req.getTimeOfDay() != null) scenario.setTimeOfDay(req.getTimeOfDay());
        if (req.getMoonIllumination() != null) scenario.setMoonIllumination(req.getMoonIllumination());
        if (req.getTerrainType() != null) scenario.setTerrainType(req.getTerrainType());
        if (req.getRunwayCondition() != null) scenario.setRunwayCondition(req.getRunwayCondition());
        if (req.getRunwayLengthFt() != null) scenario.setRunwayLengthFt(req.getRunwayLengthFt());
        if (req.getAirportElevationFt() != null) scenario.setAirportElevationFt(req.getAirportElevationFt());
        if (req.getMissionType() != null) scenario.setMissionType(req.getMissionType());
        if (req.getEmergencyType() != null) scenario.setEmergencyType(req.getEmergencyType());
        if (req.getDifficultyPreset() != null) scenario.setDifficultyPreset(req.getDifficultyPreset());
    }
}

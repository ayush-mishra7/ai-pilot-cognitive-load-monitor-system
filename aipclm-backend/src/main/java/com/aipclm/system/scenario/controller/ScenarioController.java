package com.aipclm.system.scenario.controller;

import com.aipclm.system.scenario.dto.ScenarioRequest;
import com.aipclm.system.scenario.model.FlightScenario;
import com.aipclm.system.scenario.service.ScenarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/scenario")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenarioService;

    /**
     * Create a scenario for a session (called when starting a new flight).
     */
    @PostMapping("/{sessionId}")
    public ResponseEntity<ScenarioResponse> createScenario(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) ScenarioRequest request) {
        FlightScenario scenario = scenarioService.createScenario(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(scenario));
    }

    /**
     * Get the scenario for a session.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ScenarioResponse> getScenario(@PathVariable UUID sessionId) {
        FlightScenario scenario = scenarioService.getScenario(sessionId);
        if (scenario == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toResponse(scenario));
    }

    /**
     * Update the scenario mid-flight (e.g., inject an emergency).
     */
    @PutMapping("/{sessionId}")
    public ResponseEntity<ScenarioResponse> updateScenario(
            @PathVariable UUID sessionId,
            @RequestBody ScenarioRequest request) {
        FlightScenario scenario = scenarioService.updateScenario(sessionId, request);
        return ResponseEntity.ok(toResponse(scenario));
    }

    /* ─── Response mapping ─── */

    private ScenarioResponse toResponse(FlightScenario s) {
        return ScenarioResponse.builder()
                .id(s.getId())
                .flightSessionId(s.getFlightSession().getId())
                .weatherCondition(s.getWeatherCondition().name())
                .visibility(s.getVisibility().name())
                .windSpeedKnots(s.getWindSpeedKnots())
                .windGustKnots(s.getWindGustKnots())
                .crosswindComponent(s.getCrosswindComponent())
                .temperatureC(s.getTemperatureC())
                .timeOfDay(s.getTimeOfDay().name())
                .moonIllumination(s.getMoonIllumination())
                .terrainType(s.getTerrainType().name())
                .runwayCondition(s.getRunwayCondition().name())
                .runwayLengthFt(s.getRunwayLengthFt())
                .airportElevationFt(s.getAirportElevationFt())
                .missionType(s.getMissionType().name())
                .emergencyType(s.getEmergencyType().name())
                .difficultyPreset(s.getDifficultyPreset().name())
                .build();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ScenarioResponse {
        private UUID id;
        private UUID flightSessionId;
        private String weatherCondition;
        private String visibility;
        private int windSpeedKnots;
        private int windGustKnots;
        private double crosswindComponent;
        private int temperatureC;
        private String timeOfDay;
        private double moonIllumination;
        private String terrainType;
        private String runwayCondition;
        private int runwayLengthFt;
        private int airportElevationFt;
        private String missionType;
        private String emergencyType;
        private String difficultyPreset;
    }
}

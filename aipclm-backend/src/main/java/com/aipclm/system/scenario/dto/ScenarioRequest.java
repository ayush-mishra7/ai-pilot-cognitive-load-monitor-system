package com.aipclm.system.scenario.dto;

import com.aipclm.system.scenario.model.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScenarioRequest {

    /* Weather & Atmosphere */
    private WeatherCondition weatherCondition;
    private VisibilityLevel visibility;
    private Integer windSpeedKnots;
    private Integer windGustKnots;
    private Double crosswindComponent;
    private Integer temperatureC;

    /* Time & Lighting */
    private TimeOfDay timeOfDay;
    private Double moonIllumination;

    /* Terrain & Airport */
    private TerrainType terrainType;
    private RunwayCondition runwayCondition;
    private Integer runwayLengthFt;
    private Integer airportElevationFt;

    /* Mission Profile */
    private MissionType missionType;
    private EmergencyType emergencyType;

    /* Overall Difficulty */
    private DifficultyPreset difficultyPreset;
}

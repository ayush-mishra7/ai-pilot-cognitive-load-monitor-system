package com.aipclm.system.cognitive.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionRequest {
    private double expertComputedLoad;
    private int reactionTimeMs;
    private double turbulenceLevel;
    private double stressIndex;
    private double fatigueIndex;
    private String phaseOfFlight;
    // Extended features for trained model
    private double weatherSeverity;
    private double heartRate;
    private double blinkRate;
    private double controlJitterIndex;
    private double checklistDelaySeconds;
    private double taskSwitchRate;
    private int errorCount;
    private double altitude;
    private double airspeed;
    private double verticalSpeed;
}

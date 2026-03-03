package com.aipclm.system.cognitive.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionResponse {

    @JsonProperty("predicted_load")
    private double predictedLoad;

    @JsonProperty("error_probability")
    private double errorProbability;

    @JsonProperty("confidence_score")
    private double confidenceScore;

    @JsonProperty("model_version")
    @Builder.Default
    private String modelVersion = "unknown";
}

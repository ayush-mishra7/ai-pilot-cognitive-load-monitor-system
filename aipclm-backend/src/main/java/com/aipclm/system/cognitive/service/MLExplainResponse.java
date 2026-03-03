package com.aipclm.system.cognitive.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLExplainResponse {

    @JsonProperty("predicted_load")
    private double predictedLoad;

    @JsonProperty("base_value")
    private double baseValue;

    @JsonProperty("feature_contributions")
    private List<FeatureContribution> featureContributions;

    @JsonProperty("top_positive_drivers")
    private List<String> topPositiveDrivers;

    @JsonProperty("top_negative_drivers")
    private List<String> topNegativeDrivers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureContribution {
        private String feature;
        private double value;

        @JsonProperty("shap_value")
        private double shapValue;
    }
}

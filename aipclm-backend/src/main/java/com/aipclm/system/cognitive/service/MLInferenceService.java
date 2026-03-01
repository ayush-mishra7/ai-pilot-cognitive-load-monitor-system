package com.aipclm.system.cognitive.service;

import com.aipclm.system.telemetry.model.TelemetryFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Service
@Slf4j
public class MLInferenceService {

    private static final String ML_SERVICE_URL = "http://localhost:8001";
    private static final double FALLBACK_CONFIDENCE = 0.5;
    private static final Duration ML_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;

    public MLInferenceService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(ML_SERVICE_URL)
                .build();
    }

    /**
     * Calls the external ML prediction service with a bounded timeout.
     * On ANY failure (network, timeout, malformed response, null fields)
     * returns a safe fallback: predictedLoad = expertComputedLoad,
     * errorProbability = expertComputedLoad / 100, confidenceScore = 0.50.
     */
    public MLPredictionResponse callPredictionAPI(TelemetryFrame frame, double expertComputedLoad) {
        MLPredictionRequest request = MLPredictionRequest.builder()
                .expertComputedLoad(expertComputedLoad)
                .reactionTimeMs(frame.getReactionTimeMs())
                .turbulenceLevel(frame.getTurbulenceLevel())
                .stressIndex(frame.getStressIndex())
                .fatigueIndex(frame.getFatigueIndex())
                .phaseOfFlight(frame.getPhaseOfFlight().name())
                .build();

        log.info("[ML] Calling prediction API for frame={} phase={} expertLoad={}",
                frame.getFrameNumber(), frame.getPhaseOfFlight(), expertComputedLoad);

        try {
            MLPredictionResponse response = webClient.post()
                    .uri("/predict")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(MLPredictionResponse.class)
                    .timeout(ML_TIMEOUT)
                    .block();

            if (response == null) {
                log.warn("[ML] Received null response from ML service. Falling back.");
                return buildFallback(expertComputedLoad);
            }

            // Validate predicted load is in bounds
            if (response.getPredictedLoad() < 0 || response.getPredictedLoad() > 100) {
                log.warn("[ML] predictedLoad={} is out of [0,100] bounds. Clamping.",
                        response.getPredictedLoad());
                response.setPredictedLoad(Math.max(0, Math.min(100, response.getPredictedLoad())));
            }

            log.info("[ML] Prediction success: predictedLoad={} errorProb={} confidence={}",
                    response.getPredictedLoad(), response.getErrorProbability(), response.getConfidenceScore());

            return response;

        } catch (Exception ex) {
            log.error("[ML] ML service call failed for frame={}: {}. Using fallback.",
                    frame.getFrameNumber(), ex.getMessage());
            return buildFallback(expertComputedLoad);
        }
    }

    /**
     * Deterministic safe fallback when ML service is unavailable.
     * predictedLoad echoes the expert baseline, confidence is set to
     * FALLBACK_CONFIDENCE (0.5) so the risk engine will NOT escalate
     * to CRITICAL (which requires confidence >= 0.7).
     */
    private MLPredictionResponse buildFallback(double expertComputedLoad) {
        return MLPredictionResponse.builder()
                .predictedLoad(expertComputedLoad)
                .errorProbability(expertComputedLoad / 100.0)
                .confidenceScore(FALLBACK_CONFIDENCE)
                .build();
    }
}

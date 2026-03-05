package com.aipclm.system.cognitive.service;

import com.aipclm.system.telemetry.model.TelemetryFrame;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MLInferenceService {

    private static final double FALLBACK_CONFIDENCE = 0.5;

    private final WebClient webClient;
    private final Duration mlTimeout;
    private final Timer mlInferenceTimer;
    private final Counter mlFallbackCounter;

    public MLInferenceService(
            WebClient.Builder webClientBuilder,
            @Value("${ml.service.url:http://localhost:8001}") String mlServiceUrl,
            @Value("${ml.service.timeout-ms:5000}") long timeoutMs,
            Timer mlInferenceTimer,
            Counter mlFallbackCounter) {
        this.webClient = webClientBuilder
                .baseUrl(mlServiceUrl)
                .build();
        this.mlTimeout = Duration.ofMillis(timeoutMs);
        this.mlInferenceTimer = mlInferenceTimer;
        this.mlFallbackCounter = mlFallbackCounter;
        log.info("[ML] Service configured: url={} timeout={}ms", mlServiceUrl, timeoutMs);
    }

    /**
     * Calls the external ML prediction service with extended telemetry features.
     * On ANY failure returns a safe fallback with reduced confidence.
     */
    public MLPredictionResponse callPredictionAPI(TelemetryFrame frame, double expertComputedLoad) {
        MLPredictionRequest request = MLPredictionRequest.builder()
                .expertComputedLoad(expertComputedLoad)
                .reactionTimeMs(frame.getReactionTimeMs())
                .turbulenceLevel(frame.getTurbulenceLevel())
                .stressIndex(frame.getStressIndex())
                .fatigueIndex(frame.getFatigueIndex())
                .phaseOfFlight(frame.getPhaseOfFlight().name())
                // Extended features for trained model
                .weatherSeverity(frame.getWeatherSeverity())
                .heartRate(frame.getHeartRate())
                .blinkRate(frame.getBlinkRate())
                .controlJitterIndex(frame.getControlJitterIndex())
                .checklistDelaySeconds(frame.getChecklistDelaySeconds())
                .taskSwitchRate(frame.getTaskSwitchRate())
                .errorCount(frame.getErrorCount())
                .altitude(frame.getAltitude())
                .airspeed(frame.getAirspeed())
                .verticalSpeed(frame.getVerticalSpeed())
                .build();

        log.info("[ML] Calling prediction API for frame={} phase={} expertLoad={}",
                frame.getFrameNumber(), frame.getPhaseOfFlight(), expertComputedLoad);

        long startNanos = System.nanoTime();
        try {
            MLPredictionResponse response = webClient.post()
                    .uri("/predict")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(MLPredictionResponse.class)
                    .timeout(mlTimeout)
                    .block();

            mlInferenceTimer.record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);

            if (response == null) {
                log.warn("[ML] Received null response from ML service. Falling back.");
                mlFallbackCounter.increment();
                return buildFallback(expertComputedLoad);
            }

            if (response.getPredictedLoad() < 0 || response.getPredictedLoad() > 100) {
                log.warn("[ML] predictedLoad={} out of bounds. Clamping.", response.getPredictedLoad());
                response.setPredictedLoad(Math.max(0, Math.min(100, response.getPredictedLoad())));
            }

            log.info("[ML] Prediction success: predictedLoad={} errorProb={} confidence={} model={}",
                    response.getPredictedLoad(), response.getErrorProbability(),
                    response.getConfidenceScore(), response.getModelVersion());

            return response;

        } catch (Exception ex) {
            mlInferenceTimer.record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
            mlFallbackCounter.increment();
            log.error("[ML] ML service call failed for frame={}: {}. Using fallback.",
                    frame.getFrameNumber(), ex.getMessage());
            return buildFallback(expertComputedLoad);
        }
    }

    /**
     * Calls the /explain endpoint for SHAP feature contributions.
     * Returns null on failure (explainability is non-critical).
     */
    @SuppressWarnings("unchecked")
    public MLExplainResponse callExplainAPI(TelemetryFrame frame, double expertComputedLoad) {
        MLPredictionRequest request = MLPredictionRequest.builder()
                .expertComputedLoad(expertComputedLoad)
                .reactionTimeMs(frame.getReactionTimeMs())
                .turbulenceLevel(frame.getTurbulenceLevel())
                .stressIndex(frame.getStressIndex())
                .fatigueIndex(frame.getFatigueIndex())
                .phaseOfFlight(frame.getPhaseOfFlight().name())
                .weatherSeverity(frame.getWeatherSeverity())
                .heartRate(frame.getHeartRate())
                .blinkRate(frame.getBlinkRate())
                .controlJitterIndex(frame.getControlJitterIndex())
                .checklistDelaySeconds(frame.getChecklistDelaySeconds())
                .taskSwitchRate(frame.getTaskSwitchRate())
                .errorCount(frame.getErrorCount())
                .altitude(frame.getAltitude())
                .airspeed(frame.getAirspeed())
                .verticalSpeed(frame.getVerticalSpeed())
                .build();

        try {
            MLExplainResponse response = webClient.post()
                    .uri("/explain")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(MLExplainResponse.class)
                    .timeout(mlTimeout)
                    .block();

            if (response != null) {
                log.debug("[ML] Explain success: {} feature contributions, top driver={}",
                        response.getFeatureContributions() != null ? response.getFeatureContributions().size() : 0,
                        response.getTopPositiveDrivers() != null && !response.getTopPositiveDrivers().isEmpty()
                                ? response.getTopPositiveDrivers().get(0) : "none");
            }
            return response;

        } catch (Exception ex) {
            log.warn("[ML] Explain API call failed: {}. Explainability unavailable.", ex.getMessage());
            return null;
        }
    }

    /**
     * Deterministic safe fallback when ML service is unavailable.
     */
    private MLPredictionResponse buildFallback(double expertComputedLoad) {
        return MLPredictionResponse.builder()
                .predictedLoad(expertComputedLoad)
                .errorProbability(expertComputedLoad / 100.0)
                .confidenceScore(FALLBACK_CONFIDENCE)
                .modelVersion("fallback")
                .build();
    }
}

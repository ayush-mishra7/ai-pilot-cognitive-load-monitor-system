package com.aipclm.system.cognitive.service;

import com.aipclm.system.TestFixtures;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MLInferenceService")
class MLInferenceServiceTest {

    private MLInferenceService service;
    @Mock private WebClient webClient;
    @Mock private RequestBodyUriSpec requestBodyUriSpec;
    @Mock private RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private ResponseSpec responseSpec;

    private TelemetryFrame frame;

    @BeforeEach
    void setUp() throws Exception {
        Pilot pilot = TestFixtures.pilotNovice();
        FlightSession session = TestFixtures.runningSession(pilot);
        frame = TestFixtures.cruiseFrame(session, 1);

        // Use a mocked WebClient.Builder to return our mock WebClient
        WebClient.Builder mockBuilder = mock(WebClient.Builder.class, inv -> {
            // Any method returns the builder itself, except build() which returns webClient
            if (inv.getMethod().getName().equals("build")) return webClient;
            if (inv.getMethod().getReturnType().isAssignableFrom(WebClient.Builder.class)) return inv.getMock();
            return null;
        });

        service = new MLInferenceService(mockBuilder);
    }

    @SuppressWarnings("unchecked")
    private void stubSuccessfulCall(MLPredictionResponse response) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodyUriSpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestHeadersSpec).when(requestBodyUriSpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(MLPredictionResponse.class))
                .thenReturn(Mono.just(response));
    }

    @SuppressWarnings("unchecked")
    private void stubFailedCall(Exception ex) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodyUriSpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestHeadersSpec).when(requestBodyUriSpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(MLPredictionResponse.class))
                .thenReturn(Mono.error(ex));
    }

    @SuppressWarnings("unchecked")
    private void stubNullResponse() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodyUriSpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestHeadersSpec).when(requestBodyUriSpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(MLPredictionResponse.class))
                .thenReturn(Mono.empty());
    }

    // ──────────────────── ML Service UP ────────────────────

    @Nested
    @DisplayName("When ML Service UP")
    class MLServiceUp {

        @Test
        @DisplayName("Returns ML prediction with adjusted load")
        void returnsMLPrediction() {
            MLPredictionResponse expected = MLPredictionResponse.builder()
                    .predictedLoad(45.0).errorProbability(0.45).confidenceScore(0.85).build();
            stubSuccessfulCall(expected);

            MLPredictionResponse resp = service.callPredictionAPI(frame, 40.0);

            assertThat(resp.getPredictedLoad()).isEqualTo(45.0);
            assertThat(resp.getErrorProbability()).isEqualTo(0.45);
            assertThat(resp.getConfidenceScore()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("Clamps predictedLoad > 100")
        void clampsHighLoad() {
            stubSuccessfulCall(MLPredictionResponse.builder()
                    .predictedLoad(120.0).errorProbability(0.5).confidenceScore(0.85).build());

            MLPredictionResponse resp = service.callPredictionAPI(frame, 90.0);

            assertThat(resp.getPredictedLoad()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("Clamps negative predictedLoad to 0")
        void clampsNegativeLoad() {
            stubSuccessfulCall(MLPredictionResponse.builder()
                    .predictedLoad(-5.0).errorProbability(0.0).confidenceScore(0.85).build());

            MLPredictionResponse resp = service.callPredictionAPI(frame, 10.0);

            assertThat(resp.getPredictedLoad()).isEqualTo(0.0);
        }
    }

    // ──────────────────── ML Service DOWN ────────────────────

    @Nested
    @DisplayName("When ML Service DOWN")
    class MLServiceDown {

        @Test
        @DisplayName("Returns fallback where predictedLoad = expertComputedLoad")
        void fallbackTriggered() {
            stubFailedCall(new RuntimeException("Connection refused"));

            MLPredictionResponse resp = service.callPredictionAPI(frame, 55.0);

            assertThat(resp.getPredictedLoad()).isEqualTo(55.0);
            assertThat(resp.getConfidenceScore()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("Fallback confidence is always 0.5 (blocks CRITICAL)")
        void fallbackConfidenceBlocks() {
            stubFailedCall(new RuntimeException("Timeout"));

            MLPredictionResponse resp = service.callPredictionAPI(frame, 95.0);

            assertThat(resp.getConfidenceScore()).isEqualTo(0.5);
            assertThat(resp.getPredictedLoad()).isEqualTo(95.0);
            assertThat(resp.getErrorProbability()).isCloseTo(0.95, within(0.01));
        }

        @Test
        @DisplayName("No exception propagated — service does not crash")
        void noCrashOnFailure() {
            stubFailedCall(new RuntimeException("ML Service unreachable"));

            assertThatCode(() -> service.callPredictionAPI(frame, 50.0))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Null response → returns fallback")
        void nullResponseFallback() {
            stubNullResponse();

            MLPredictionResponse resp = service.callPredictionAPI(frame, 60.0);

            assertThat(resp.getPredictedLoad()).isEqualTo(60.0);
            assertThat(resp.getConfidenceScore()).isEqualTo(0.5);
        }
    }

    // ──────────────────── Timeout Tests ────────────────────

    @Nested
    @DisplayName("Timeout Tests")
    class TimeoutTests {

        @Test
        @DisplayName("ML response delayed → fallback returned (no hang)")
        void timeoutReturnsFallback() {
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            doReturn(requestBodyUriSpec).when(requestBodyUriSpec).uri(anyString());
            doReturn(requestHeadersSpec).when(requestBodyUriSpec).bodyValue(any());
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            // Simulate a Mono that never completes until timeout
            when(responseSpec.bodyToMono(MLPredictionResponse.class))
                    .thenReturn(Mono.delay(Duration.ofSeconds(10)).then(Mono.empty()));

            MLPredictionResponse resp = service.callPredictionAPI(frame, 50.0);

            assertThat(resp.getPredictedLoad()).isEqualTo(50.0);
            assertThat(resp.getConfidenceScore()).isEqualTo(0.5);
        }
    }
}

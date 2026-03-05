package com.aipclm.system.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 7: Custom Micrometer metrics for the AI-PCLM simulation pipeline.
 *
 * Exposes:
 *   aipclm.pipeline.steps          — counter of simulation pipeline steps executed
 *   aipclm.pipeline.failures       — counter of pipeline step failures
 *   aipclm.pipeline.step.duration  — timer for end-to-end pipeline step latency
 *   aipclm.ml.inference.duration   — timer for ML service call latency
 *   aipclm.ml.inference.fallbacks  — counter of ML fallback activations
 *   aipclm.sessions.active         — gauge of running sessions (registered externally)
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter pipelineStepCounter(MeterRegistry registry) {
        return Counter.builder("aipclm.pipeline.steps")
                .description("Total simulation pipeline steps executed")
                .register(registry);
    }

    @Bean
    public Counter pipelineFailureCounter(MeterRegistry registry) {
        return Counter.builder("aipclm.pipeline.failures")
                .description("Total simulation pipeline step failures")
                .register(registry);
    }

    @Bean
    public Timer pipelineStepTimer(MeterRegistry registry) {
        return Timer.builder("aipclm.pipeline.step.duration")
                .description("End-to-end pipeline step latency")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(registry);
    }

    @Bean
    public Timer mlInferenceTimer(MeterRegistry registry) {
        return Timer.builder("aipclm.ml.inference.duration")
                .description("ML service inference call latency")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(registry);
    }

    @Bean
    public Counter mlFallbackCounter(MeterRegistry registry) {
        return Counter.builder("aipclm.ml.inference.fallbacks")
                .description("ML inference fallback activations")
                .register(registry);
    }
}

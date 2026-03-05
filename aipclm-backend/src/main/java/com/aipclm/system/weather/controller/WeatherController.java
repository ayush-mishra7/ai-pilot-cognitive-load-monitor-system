package com.aipclm.system.weather.controller;

import com.aipclm.system.weather.model.WeatherObservation;
import com.aipclm.system.weather.model.WeatherReportType;
import com.aipclm.system.weather.repository.WeatherObservationRepository;
import com.aipclm.system.weather.service.WeatherService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST endpoints for aviation weather data (METAR/TAF).
 */
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final WeatherObservationRepository repository;

    /**
     * Fetch fresh METAR for an ICAO station.
     * Uses live API if configured, otherwise generates synthetic weather.
     */
    @PostMapping("/metar/{icao}")
    public ResponseEntity<WeatherDto> fetchMetar(@PathVariable String icao) {
        WeatherObservation obs = weatherService.fetchMetar(icao);
        return ResponseEntity.ok(toDto(obs));
    }

    /**
     * Fetch fresh TAF for an ICAO station.
     */
    @PostMapping("/taf/{icao}")
    public ResponseEntity<WeatherDto> fetchTaf(@PathVariable String icao) {
        WeatherObservation obs = weatherService.fetchTaf(icao);
        return ResponseEntity.ok(toDto(obs));
    }

    /**
     * Get the latest cached METAR for a station (no API call).
     */
    @GetMapping("/metar/{icao}")
    public ResponseEntity<WeatherDto> getLatestMetar(@PathVariable String icao) {
        return weatherService.getLatestCached(icao)
                .map(obs -> ResponseEntity.ok(toDto(obs)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get recent weather history for a station (up to 20 records).
     */
    @GetMapping("/history/{icao}")
    public ResponseEntity<List<WeatherDto>> getWeatherHistory(@PathVariable String icao) {
        List<WeatherDto> history = weatherService.getRecentHistory(icao).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    private WeatherDto toDto(WeatherObservation obs) {
        return WeatherDto.builder()
                .id(obs.getId() != null ? obs.getId().toString() : null)
                .icaoCode(obs.getIcaoCode())
                .reportType(obs.getReportType().name())
                .rawText(obs.getRawText())
                .temperatureC(obs.getTemperatureC())
                .dewpointC(obs.getDewpointC())
                .windDirectionDeg(obs.getWindDirectionDeg())
                .windSpeedKts(obs.getWindSpeedKts())
                .windGustKts(obs.getWindGustKts())
                .visibilitySm(obs.getVisibilitySm())
                .altimeterInhg(obs.getAltimeterInhg())
                .ceilingFt(obs.getCeilingFt())
                .flightCategory(obs.getFlightCategory() != null ? obs.getFlightCategory().name() : null)
                .precipitation(obs.getPrecipitation())
                .icingReported(obs.isIcingReported())
                .windShearReported(obs.isWindShearReported())
                .thunderstormReported(obs.isThunderstormReported())
                .weatherSeverityScore(obs.getWeatherSeverityScore())
                .observedAt(obs.getObservedAt())
                .build();
    }

    @Data
    @Builder
    public static class WeatherDto {
        private String id;
        private String icaoCode;
        private String reportType;
        private String rawText;
        private Double temperatureC;
        private Double dewpointC;
        private Integer windDirectionDeg;
        private Integer windSpeedKts;
        private Integer windGustKts;
        private Double visibilitySm;
        private Double altimeterInhg;
        private Integer ceilingFt;
        private String flightCategory;
        private String precipitation;
        private boolean icingReported;
        private boolean windShearReported;
        private boolean thunderstormReported;
        private double weatherSeverityScore;
        private Instant observedAt;
    }
}

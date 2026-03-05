package com.aipclm.system.weather.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores a parsed METAR/TAF weather observation from an aviation weather API.
 * Each record represents a single weather report for an ICAO airport code.
 */
@Entity
@Table(name = "weather_observation", indexes = {
        @Index(name = "idx_weather_icao_observed", columnList = "icao_code, observed_at DESC")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** ICAO airport identifier, e.g. KJFK, EGLL, VABB */
    @Column(name = "icao_code", nullable = false, length = 4)
    private String icaoCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WeatherReportType reportType = WeatherReportType.METAR;

    /** Raw METAR/TAF string as received from the API */
    @Column(columnDefinition = "TEXT")
    private String rawText;

    // ── Parsed weather fields ──

    /** Temperature in degrees Celsius */
    @Column
    private Double temperatureC;

    /** Dewpoint in degrees Celsius */
    @Column
    private Double dewpointC;

    /** Wind direction in degrees (0-360) */
    @Column
    private Integer windDirectionDeg;

    /** Wind speed in knots */
    @Column
    private Integer windSpeedKts;

    /** Wind gust speed in knots (null if no gusts) */
    @Column
    private Integer windGustKts;

    /** Visibility in statute miles */
    @Column
    private Double visibilitySm;

    /** Altimeter setting in inches of mercury (inHg) */
    @Column
    private Double altimeterInhg;

    /** Cloud ceiling in feet AGL. Null if sky clear / unlimited. */
    @Column
    private Integer ceilingFt;

    /** Flight category: VFR, MVFR, IFR, LIFR */
    @Enumerated(EnumType.STRING)
    @Column
    private FlightCategory flightCategory;

    /** Precipitation type description (e.g. "RA", "SN", "TS", null if none) */
    @Column(length = 20)
    private String precipitation;

    /** Icing reported (PIREP or forecast) */
    @Builder.Default
    @Column(nullable = false)
    private boolean icingReported = false;

    /** Wind shear reported */
    @Builder.Default
    @Column(nullable = false)
    private boolean windShearReported = false;

    /** Thunderstorm activity reported */
    @Builder.Default
    @Column(nullable = false)
    private boolean thunderstormReported = false;

    /** Computed weather severity score 0.0 (benign) → 1.0 (extreme) */
    @Builder.Default
    @Column(nullable = false)
    private double weatherSeverityScore = 0.0;

    /** Timestamp when the observation was made (from METAR/TAF) */
    @Column(name = "observed_at")
    private Instant observedAt;

    /** Timestamp when this record was fetched/created in our system */
    @Column(nullable = false, updatable = false)
    private Instant fetchedAt;

    @PrePersist
    protected void onCreate() {
        if (fetchedAt == null) fetchedAt = Instant.now();
    }
}

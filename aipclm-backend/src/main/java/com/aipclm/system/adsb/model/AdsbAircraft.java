package com.aipclm.system.adsb.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Represents a single ADS-B aircraft state vector, either received from
 * the OpenSky Network API or generated synthetically for simulation.
 *
 * <p>Used for shadow-monitoring real-world traffic density and TCAS-like
 * proximity alerts in research mode.</p>
 */
@Entity
@Table(name = "adsb_aircraft", indexes = {
        @Index(name = "idx_adsb_observed", columnList = "observed_at DESC"),
        @Index(name = "idx_adsb_callsign", columnList = "callsign")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdsbAircraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** ICAO 24-bit transponder address (hex string, e.g. "a1b2c3") */
    @Column(nullable = false, length = 6)
    private String icao24;

    /** Callsign (flight number, e.g. "UAL123"). May be null. */
    @Column(length = 8)
    private String callsign;

    /** Country of registration */
    @Column(length = 64)
    private String originCountry;

    /** Longitude in decimal degrees */
    @Column
    private Double longitude;

    /** Latitude in decimal degrees */
    @Column
    private Double latitude;

    /** Barometric altitude in feet */
    @Column
    private Double altitudeFt;

    /** Ground speed in knots */
    @Column
    private Double groundSpeedKts;

    /** True track heading in degrees (0-360) */
    @Column
    private Double trackDeg;

    /** Vertical rate in feet per minute */
    @Column
    private Double verticalRateFpm;

    /** Whether the aircraft is on the ground */
    @Builder.Default
    @Column(nullable = false)
    private boolean onGround = false;

    /** Squawk code (e.g. "7700" for emergency) */
    @Column(length = 4)
    private String squawk;

    /**
     * Distance from the monitoring reference point (e.g. the simulated aircraft
     * or airport) in nautical miles. Computed after ingestion.
     */
    @Column
    private Double distanceNm;

    /** The reference area center latitude used for this query */
    @Column
    private Double referenceLat;

    /** The reference area center longitude used for this query */
    @Column
    private Double referenceLon;

    /** Timestamp of the ADS-B observation */
    @Column(name = "observed_at")
    private Instant observedAt;

    /** Timestamp when this record was ingested into our system */
    @Column(nullable = false, updatable = false)
    private Instant fetchedAt;

    @PrePersist
    protected void onCreate() {
        if (fetchedAt == null) fetchedAt = Instant.now();
    }
}

package com.aipclm.system.sensor.model;

import com.aipclm.system.session.model.FlightSession;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A single data point received from a wearable sensor.
 * Stored alongside (and indexed by) flight-session + frame-number so the
 * pipeline can look up the latest sensor value for every telemetry tick.
 */
@Entity
@Table(name = "sensor_reading", indexes = {
        @Index(name = "idx_sensor_reading_session_frame",
               columnList = "flight_session_id, frame_number"),
        @Index(name = "idx_sensor_reading_device_ts",
               columnList = "sensor_device_id, timestamp")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_device_id", nullable = false)
    private SensorDevice sensorDevice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_session_id", nullable = false)
    private FlightSession flightSession;

    /** The telemetry frame this reading is associated with (may be 0 before assignment). */
    @Column(nullable = false)
    @Builder.Default
    private int frameNumber = 0;

    /** Raw value as received from the sensor hardware. */
    @Column(nullable = false)
    private double rawValue;

    /** Value after calibration (gain * raw + offset) and clamping. */
    @Column(nullable = false)
    private double normalizedValue;

    /** Physical unit of measurement, e.g. "bpm", "µV", "mm", "µS", "%", "°C". */
    @Column(nullable = false)
    private String unit;

    /** Signal quality indicator 0.0 (noise) → 1.0 (perfect). */
    @Builder.Default
    @Column(nullable = false)
    private double signalQuality = 1.0;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

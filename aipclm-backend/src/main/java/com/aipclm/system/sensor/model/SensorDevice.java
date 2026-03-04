package com.aipclm.system.sensor.model;

import com.aipclm.system.session.model.FlightSession;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered wearable / physiological sensor device.
 * A device is optionally bound to a flight session; once connected
 * its readings override the corresponding simulated biometrics.
 */
@Entity
@Table(name = "sensor_device", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"flight_session_id", "sensor_type"})
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SensorDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SensorType sensorType;

    @Column
    private String manufacturer;

    @Column
    private String modelNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_session_id")
    private FlightSession flightSession;

    /** Additive offset applied during normalisation (sensor calibration). */
    @Builder.Default
    @Column(nullable = false)
    private double calibrationOffset = 0.0;

    /** Multiplicative gain applied during normalisation. */
    @Builder.Default
    @Column(nullable = false)
    private double calibrationGain = 1.0;

    @Column
    private Instant lastDataReceivedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

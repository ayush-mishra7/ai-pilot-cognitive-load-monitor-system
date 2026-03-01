package com.aipclm.system.telemetry.model;

import com.aipclm.system.session.model.FlightSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telemetry_frame", indexes = {
        @Index(name = "idx_telemetry_frame_session_timestamp", columnList = "flight_session_id, timestamp")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryFrame {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_session_id", nullable = false)
    private FlightSession flightSession;

    @Column(nullable = false)
    private int frameNumber;

    @Column(nullable = false)
    private Instant timestamp;

    private double altitude;
    private double airspeed;
    private double verticalSpeed;
    private double heading;
    private double pitch;
    private double roll;
    private double yawRate;
    private double turbulenceLevel;
    private double weatherSeverity;
    private boolean autopilotEngaged;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhaseOfFlight phaseOfFlight;

    private int reactionTimeMs;
    private double controlInputFrequency;
    private double checklistDelaySeconds;
    private double taskSwitchRate;
    private int errorCount;
    private double controlJitterIndex;
    private double instrumentScanVariance;

    private double heartRate;
    private double blinkRate;
    private double fatigueIndex;
    private double stressIndex;

    private double altitudeDeviation;
    private double verticalSpeedInstability;
    private double airspeedDeviation;
    private double headingDeviation;
    private double pitchInstability;
    private double rollInstability;

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

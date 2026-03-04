package com.aipclm.system.telemetry.model;

import com.aipclm.system.pilot.model.CrewRole;
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

    /** Null for single-pilot sessions; CAPTAIN or FIRST_OFFICER in crew mode. */
    @Enumerated(EnumType.STRING)
    @Column
    private CrewRole crewRole;

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

    // ── Wearable sensor biometrics (Phase 5) ──
    /** Galvanic skin response / electro-dermal activity in µS. Null when no GSR sensor. */
    @Column
    private Double gsrLevel;
    /** Blood-oxygen saturation percentage (SpO2). Null when no pulse-ox sensor. */
    @Column
    private Double spO2Level;
    /** Skin temperature in °C. Null when no temp sensor. */
    @Column
    private Double skinTemperature;
    /** EEG alpha-band (8-13 Hz) power in µV². Null when no EEG sensor. */
    @Column
    private Double eegAlphaPower;
    /** EEG beta-band (13-30 Hz) power in µV². Null when no EEG sensor. */
    @Column
    private Double eegBetaPower;
    /** EEG theta-band (4-8 Hz) power in µV². Null when no EEG sensor. */
    @Column
    private Double eegThetaPower;
    /** Pupil diameter in mm from eye tracker. Null when no eye tracker. */
    @Column
    private Double pupilDiameter;
    /** Gaze fixation duration in ms from eye tracker. Null when no eye tracker. */
    @Column
    private Double gazeFixationDurationMs;
    /** True if biometrics were overridden by real sensor data rather than simulated. */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean sensorOverride = false;

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

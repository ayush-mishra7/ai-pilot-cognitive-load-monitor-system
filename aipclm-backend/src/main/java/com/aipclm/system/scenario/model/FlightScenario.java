package com.aipclm.system.scenario.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

import com.aipclm.system.session.model.FlightSession;

@Entity
@Table(name = "flight_scenario")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_session_id", nullable = false, unique = true)
    private FlightSession flightSession;

    /* ── Weather & Atmosphere ── */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WeatherCondition weatherCondition = WeatherCondition.CLEAR;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VisibilityLevel visibility = VisibilityLevel.UNLIMITED;

    @Builder.Default
    @Column(nullable = false)
    private int windSpeedKnots = 5;

    @Builder.Default
    @Column(nullable = false)
    private int windGustKnots = 0;

    @Builder.Default
    @Column(nullable = false)
    private double crosswindComponent = 0.0;

    @Builder.Default
    @Column(nullable = false)
    private int temperatureC = 15;

    /* ── Time & Lighting ── */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TimeOfDay timeOfDay = TimeOfDay.DAY;

    @Builder.Default
    @Column(nullable = false)
    private double moonIllumination = 0.0;

    /* ── Terrain & Airport ── */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TerrainType terrainType = TerrainType.FLAT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RunwayCondition runwayCondition = RunwayCondition.DRY;

    @Builder.Default
    @Column(nullable = false)
    private int runwayLengthFt = 10000;

    @Builder.Default
    @Column(nullable = false)
    private int airportElevationFt = 500;

    /* ── Mission Profile ── */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MissionType missionType = MissionType.ROUTINE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EmergencyType emergencyType = EmergencyType.NONE;

    /* ── Overall Difficulty ── */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DifficultyPreset difficultyPreset = DifficultyPreset.NORMAL;

    /* ── Timestamps ── */

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

package com.aipclm.system.crm.model;

import com.aipclm.system.session.model.FlightSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Per-tick Crew Resource Management assessment for a crew-mode session.
 * Captures cross-crew interaction metrics including communication quality,
 * workload distribution, authority gradient, and CRM effectiveness.
 */
@Entity
@Table(name = "crm_assessment", indexes = {
        @Index(name = "idx_crm_assessment_session_frame", columnList = "flight_session_id, frame_number")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_session_id", nullable = false)
    private FlightSession flightSession;

    @Column(name = "frame_number", nullable = false)
    private int frameNumber;

    /** Communication effectiveness between crew members (0–100). */
    @Column(nullable = false)
    private double communicationScore;

    /** How evenly workload is shared: 0.5 = perfect split, 0 or 1 = one-sided. */
    @Column(nullable = false)
    private double workloadDistribution;

    /** Authority gradient: 0 = flat (equal), 1 = steep (captain dominant). */
    @Column(nullable = false)
    private double authorityGradient;

    /** Combined situational awareness score (0–100). */
    @Column(nullable = false)
    private double situationalAwarenessScore;

    /** Magnitude of cross-crew stress propagation (0–1). */
    @Column(nullable = false)
    private double crossCrewStressContagion;

    /** Overall CRM effectiveness score (0–100). */
    @Column(nullable = false)
    private double crmEffectivenessScore;

    /** Fatigue symmetry between crew: 1 = identical, 0 = maximally asymmetric. */
    @Column(nullable = false)
    private double fatigueSymmetry;

    /** Captain's smoothed cognitive load at this frame. */
    @Column(nullable = false)
    private double captainLoad;

    /** First Officer's smoothed cognitive load at this frame. */
    @Column(nullable = false)
    private double firstOfficerLoad;

    @Column(nullable = false)
    private Instant timestamp;

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

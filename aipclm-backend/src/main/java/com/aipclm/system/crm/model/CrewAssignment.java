package com.aipclm.system.crm.model;

import com.aipclm.system.pilot.model.CrewRole;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.session.model.FlightSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Links a {@link Pilot} to a {@link FlightSession} with a specific {@link CrewRole}.
 * Each crew-mode session has exactly two assignments: CAPTAIN and FIRST_OFFICER.
 */
@Entity
@Table(name = "crew_assignments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"flight_session_id", "crew_role"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrewAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_session_id", nullable = false)
    private FlightSession flightSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pilot_id", nullable = false)
    private Pilot pilot;

    @Enumerated(EnumType.STRING)
    @Column(name = "crew_role", nullable = false)
    private CrewRole crewRole;

    /** True if this crew member is the Pilot Flying (PF) for this session. */
    @Builder.Default
    @Column(nullable = false)
    private boolean pilotFlying = false;

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

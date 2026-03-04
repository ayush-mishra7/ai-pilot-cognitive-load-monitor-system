package com.aipclm.system.telemetry.repository;

import com.aipclm.system.pilot.model.CrewRole;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelemetryFrameRepository extends JpaRepository<TelemetryFrame, UUID> {
    Optional<TelemetryFrame> findTopByFlightSessionIdOrderByFrameNumberDesc(UUID flightSessionId);

    /** Latest frame for a specific crew role in a session (crew mode). */
    Optional<TelemetryFrame> findTopByFlightSessionIdAndCrewRoleOrderByFrameNumberDesc(
            UUID flightSessionId, CrewRole crewRole);

    /** All frames for a given session + frame number (returns 1 for single-pilot, 2 for crew). */
    List<TelemetryFrame> findByFlightSessionIdAndFrameNumber(UUID flightSessionId, int frameNumber);
}

package com.aipclm.system.telemetry.repository;

import com.aipclm.system.telemetry.model.TelemetryFrame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelemetryFrameRepository extends JpaRepository<TelemetryFrame, UUID> {
    Optional<TelemetryFrame> findTopByFlightSessionIdOrderByFrameNumberDesc(UUID flightSessionId);
}

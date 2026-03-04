package com.aipclm.system.crm.repository;

import com.aipclm.system.crm.model.CrewAssignment;
import com.aipclm.system.pilot.model.CrewRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrewAssignmentRepository extends JpaRepository<CrewAssignment, UUID> {

    List<CrewAssignment> findByFlightSessionId(UUID sessionId);

    Optional<CrewAssignment> findByFlightSessionIdAndCrewRole(UUID sessionId, CrewRole crewRole);

    Optional<CrewAssignment> findByFlightSessionIdAndPilotFlyingTrue(UUID sessionId);
}

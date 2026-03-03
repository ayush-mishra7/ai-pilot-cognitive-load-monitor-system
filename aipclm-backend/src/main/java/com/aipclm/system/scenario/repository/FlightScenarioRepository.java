package com.aipclm.system.scenario.repository;

import com.aipclm.system.scenario.model.FlightScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlightScenarioRepository extends JpaRepository<FlightScenario, UUID> {

    Optional<FlightScenario> findByFlightSessionId(UUID flightSessionId);

    boolean existsByFlightSessionId(UUID flightSessionId);
}

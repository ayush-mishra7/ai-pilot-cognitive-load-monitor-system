package com.aipclm.system.pilot.repository;

import com.aipclm.system.pilot.model.Pilot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PilotRepository extends JpaRepository<Pilot, UUID> {

    Optional<Pilot> findByUserId(UUID userId);
}

package com.aipclm.system.session.repository;

import com.aipclm.system.session.model.FlightSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FlightSessionRepository extends JpaRepository<FlightSession, UUID> {
}

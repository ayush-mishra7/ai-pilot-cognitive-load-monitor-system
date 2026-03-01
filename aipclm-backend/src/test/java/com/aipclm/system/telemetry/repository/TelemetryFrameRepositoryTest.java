package com.aipclm.system.telemetry.repository;

import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.pilot.model.PilotProfileType;
import com.aipclm.system.pilot.repository.PilotRepository;
import com.aipclm.system.session.model.FlightSession;
import com.aipclm.system.session.model.FlightSessionStatus;
import com.aipclm.system.session.repository.FlightSessionRepository;
import com.aipclm.system.telemetry.model.PhaseOfFlight;
import com.aipclm.system.telemetry.model.TelemetryFrame;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TelemetryFrameRepositoryTest {

    @Autowired
    private PilotRepository pilotRepository;

    @Autowired
    private FlightSessionRepository sessionRepository;

    @Autowired
    private TelemetryFrameRepository telemetryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldSaveAndLazyLoadFlightSession() {
        Pilot pilot = Pilot.builder()
                .fullName("Test Pilot")
                .profileType(PilotProfileType.EXPERIENCED)
                .baselineStressSensitivity(1.0)
                .baselineFatigueRate(1.0)
                .build();
        pilot = pilotRepository.saveAndFlush(pilot);

        FlightSession session = FlightSession.builder()
                .pilot(pilot)
                .sessionStartTime(Instant.now())
                .status(FlightSessionStatus.RUNNING)
                .build();
        session = sessionRepository.saveAndFlush(session);

        TelemetryFrame frame = TelemetryFrame.builder()
                .flightSession(session)
                .frameNumber(1)
                .timestamp(Instant.now())
                .altitude(10000)
                .airspeed(250)
                .phaseOfFlight(PhaseOfFlight.CRUISE)
                .build();
        TelemetryFrame savedFrame = telemetryRepository.saveAndFlush(frame);

        // Detach entities to test lazy loading from DB
        entityManager.clear();

        TelemetryFrame retrievedFrame = telemetryRepository.findById(savedFrame.getId()).orElseThrow();

        // Verify UUID generation and basic mappings
        assertThat(retrievedFrame.getId()).isNotNull();
        assertThat(retrievedFrame.getCreatedAt()).isNotNull();
        assertThat(retrievedFrame.getPhaseOfFlight()).isEqualTo(PhaseOfFlight.CRUISE);

        // Verify ManyToOne lazy loading works (should not be initialized until
        // accessed)
        assertThat(Hibernate.isInitialized(retrievedFrame.getFlightSession())).isFalse();

        // Access proxy to trigger initialization (getId won't trigger it)
        assertThat(retrievedFrame.getFlightSession().getStatus()).isEqualTo(FlightSessionStatus.RUNNING);
        assertThat(Hibernate.isInitialized(retrievedFrame.getFlightSession())).isTrue();
    }
}

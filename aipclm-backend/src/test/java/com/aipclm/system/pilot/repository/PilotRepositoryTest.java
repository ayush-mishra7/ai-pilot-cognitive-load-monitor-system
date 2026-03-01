package com.aipclm.system.pilot.repository;

import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.pilot.model.PilotProfileType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PilotRepositoryTest {

    @Autowired
    private PilotRepository pilotRepository;

    @Test
    void shouldSavePilotAndGenerateUUIDAndTimestamps() {
        Pilot pilot = Pilot.builder()
                .fullName("Test Pilot")
                .profileType(PilotProfileType.NOVICE)
                .baselineStressSensitivity(1.0)
                .baselineFatigueRate(1.0)
                .build();

        Pilot savedPilot = pilotRepository.save(pilot);
        pilotRepository.flush(); // Ensure insert is executed and UUID is grabbed from DB

        assertThat(savedPilot.getId()).isNotNull();
        assertThat(savedPilot.getCreatedAt()).isNotNull();
        assertThat(savedPilot.getUpdatedAt()).isNotNull();
    }
}

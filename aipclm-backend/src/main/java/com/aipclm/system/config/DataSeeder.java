package com.aipclm.system.config;

import com.aipclm.system.auth.model.User;
import com.aipclm.system.auth.model.UserRole;
import com.aipclm.system.auth.repository.UserRepository;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.pilot.model.PilotProfileType;
import com.aipclm.system.pilot.repository.PilotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the database with default PILOT and ATC accounts on first launch.
 * Skips silently if the accounts already exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PilotRepository pilotRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedPilot();
        seedAtc();
    }

    private void seedPilot() {
        String email = "pilot@aipclm.com";
        if (userRepository.existsByEmail(email)) {
            log.info("Seed pilot account already exists — skipping");
            return;
        }

        User pilot = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("pilot123"))
                .fullName("Cpt. Demo Pilot")
                .role(UserRole.PILOT)
                .callSign("ALPHA-7")
                .build();
        pilot = userRepository.save(pilot);

        Pilot profile = Pilot.builder()
                .fullName(pilot.getFullName())
                .profileType(PilotProfileType.EXPERIENCED)
                .baselineStressSensitivity(0.4)
                .baselineFatigueRate(0.3)
                .userId(pilot.getId())
                .build();
        pilotRepository.save(profile);

        log.info("Seeded default PILOT account: {} / pilot123", email);
    }

    private void seedAtc() {
        String email = "tower@aipclm.com";
        if (userRepository.existsByEmail(email)) {
            log.info("Seed ATC account already exists — skipping");
            return;
        }

        User atc = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("tower123"))
                .fullName("ATC Tower Control")
                .role(UserRole.ATC)
                .callSign("TOWER-1")
                .build();
        userRepository.save(atc);

        log.info("Seeded default ATC account: {} / tower123", email);
    }
}

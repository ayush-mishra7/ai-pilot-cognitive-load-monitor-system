package com.aipclm.system.auth.service;

import com.aipclm.system.auth.dto.AuthResponse;
import com.aipclm.system.auth.dto.LoginRequest;
import com.aipclm.system.auth.dto.RegisterRequest;
import com.aipclm.system.auth.model.User;
import com.aipclm.system.auth.model.UserRole;
import com.aipclm.system.auth.repository.UserRepository;
import com.aipclm.system.auth.security.JwtTokenProvider;
import com.aipclm.system.pilot.model.Pilot;
import com.aipclm.system.pilot.model.PilotProfileType;
import com.aipclm.system.pilot.repository.PilotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PilotRepository pilotRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Register a new user. If role is PILOT, also creates a linked Pilot profile.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Uniqueness checks
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (request.getCallSign() != null && !request.getCallSign().isBlank()
                && userRepository.existsByCallSign(request.getCallSign())) {
            throw new IllegalArgumentException("Call sign already taken");
        }

        // Create user
        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .role(request.getRole())
                .callSign(request.getCallSign() != null ? request.getCallSign().toUpperCase().trim() : null)
                .build();
        user = userRepository.save(user);

        // If PILOT, create a linked Pilot profile
        UUID pilotId = null;
        if (request.getRole() == UserRole.PILOT) {
            PilotProfileType profileType = PilotProfileType.EXPERIENCED; // default
            if (request.getProfileType() != null) {
                try {
                    profileType = PilotProfileType.valueOf(request.getProfileType().toUpperCase());
                } catch (IllegalArgumentException ignored) { /* keep default */ }
            }

            Pilot pilot = Pilot.builder()
                    .fullName(user.getFullName())
                    .profileType(profileType)
                    .baselineStressSensitivity(profileType == PilotProfileType.HIGH_STRESS ? 0.7 : 0.4)
                    .baselineFatigueRate(profileType == PilotProfileType.FATIGUE_PRONE ? 0.6 : 0.3)
                    .userId(user.getId())
                    .build();
            pilot = pilotRepository.save(pilot);
            pilotId = pilot.getId();
        }

        // Generate JWT
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        log.info("Registered user {} with role {}", user.getEmail(), user.getRole());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .callSign(user.getCallSign())
                .pilotId(pilotId)
                .build();
    }

    /**
     * Authenticate a user and return a JWT token.
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Find linked pilot (if PILOT role)
        UUID pilotId = null;
        if (user.getRole() == UserRole.PILOT) {
            pilotId = pilotRepository.findByUserId(user.getId())
                    .map(Pilot::getId)
                    .orElse(null);
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        log.info("User {} logged in", user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .callSign(user.getCallSign())
                .pilotId(pilotId)
                .build();
    }

    /**
     * Get user profile by ID (for /auth/me endpoint).
     */
    public AuthResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UUID pilotId = null;
        if (user.getRole() == UserRole.PILOT) {
            pilotId = pilotRepository.findByUserId(user.getId())
                    .map(Pilot::getId)
                    .orElse(null);
        }

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .callSign(user.getCallSign())
                .pilotId(pilotId)
                .build();
    }
}

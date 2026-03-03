package com.aipclm.system.auth.dto;

import com.aipclm.system.auth.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be 6–100 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(max = 150)
    private String fullName;

    @NotNull(message = "Role is required")
    private UserRole role;

    @Size(max = 20, message = "Call sign must be ≤ 20 characters")
    private String callSign;

    /* Pilot-specific fields (only used when role == PILOT) */
    private String profileType;  // NOVICE, EXPERIENCED, FATIGUE_PRONE, HIGH_STRESS
}

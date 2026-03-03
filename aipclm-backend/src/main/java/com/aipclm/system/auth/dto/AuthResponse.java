package com.aipclm.system.auth.dto;

import com.aipclm.system.auth.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private UUID userId;
    private String email;
    private String fullName;
    private UserRole role;
    private String callSign;

    /** Only set for PILOT users — the linked pilot profile UUID */
    private UUID pilotId;
}

package com.fleet.auth.dto;

import com.fleet.auth.model.UserRole;
import java.util.UUID;

public record RegisterResponse(
    UUID userId,
    UserRole role,
    String matricule
) {
}

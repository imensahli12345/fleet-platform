package com.fleet.auth.model;

import java.time.Instant;
import java.util.UUID;

public record AuthUser(
    UUID id,
    String fullName,
    String email,
    String passwordHash,
    UserRole role,
    Instant createdAt
) {
}

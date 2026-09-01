package com.fleet.auth.dto;

import com.fleet.auth.model.UserRole;

public record LoginResponse(
    String accessToken,
    long expiresIn,
    UserRole role
) {
}

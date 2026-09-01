package com.fleet.auth.dto;

import com.fleet.auth.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "fullName is required")
    String fullName,

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    String email,

    @NotBlank(message = "password is required")
    @Size(min = 6, message = "password must contain at least 6 characters")
    String password,

    @Size(max = 50, message = "matricule must contain at most 50 characters")
    String matricule,

    @NotNull(message = "role is required")
    UserRole role
) {
}

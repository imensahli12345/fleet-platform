package com.fleet.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateDriverRequest(
    @NotNull(message = "authUserId is required")
    UUID authUserId,

    @NotBlank(message = "fullName is required")
    String fullName,

    @NotBlank(message = "matricule is required")
    @Size(max = 50, message = "matricule must contain at most 50 characters")
    String matricule
) {
}

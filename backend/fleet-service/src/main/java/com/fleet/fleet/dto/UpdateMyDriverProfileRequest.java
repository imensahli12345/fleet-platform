package com.fleet.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMyDriverProfileRequest(
    @NotBlank(message = "licenseNumber is required")
    @Size(max = 50, message = "licenseNumber must contain at most 50 characters")
    String licenseNumber,

    @NotBlank(message = "phoneNumber is required")
    @Size(max = 30, message = "phoneNumber must contain at most 30 characters")
    String phoneNumber
) {
}

package com.fleet.fleet.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateDriverActivationRequest(
    @NotNull(message = "active is required")
    Boolean active
) {
}

package com.fleet.fleet.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignTruckRequest(
    @NotNull(message = "driverId is required")
    UUID driverId
) {
}

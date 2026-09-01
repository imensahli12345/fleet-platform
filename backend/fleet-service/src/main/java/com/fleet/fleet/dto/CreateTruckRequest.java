package com.fleet.fleet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTruckRequest(
    @NotBlank(message = "registrationNumber is required")
    String registrationNumber,

    @NotBlank(message = "brand is required")
    String brand,

    @NotBlank(message = "model is required")
    String model,

    @NotNull(message = "capacity is required")
    @DecimalMin(value = "0.1", message = "capacity must be greater than 0")
    Double capacity
) {
}

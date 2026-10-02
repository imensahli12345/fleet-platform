package com.fleet.shipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateShipmentRequest(
        @NotBlank String origin,
        @NotBlank String destination,
        @NotBlank String customerName,
        @NotNull UUID customerAuthUserId,
        UUID assignedTruckId
) {}

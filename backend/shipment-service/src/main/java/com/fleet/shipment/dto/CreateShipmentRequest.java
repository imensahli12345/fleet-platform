package com.fleet.shipment.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateShipmentRequest(
    @NotBlank(message = "Origin is required")
    String origin,

    @NotBlank(message = "Destination is required")
    String destination,

    @NotBlank(message = "Customer name is required")
    String customerName,

    // Optional: If the dispatcher wants to manually assign a truck, they can provide it.
    // If null, the service will auto-assign the first available truck via Feign.
    UUID assignedTruckId
) {
}

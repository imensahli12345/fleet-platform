package com.fleet.shipment.dto;

import java.util.UUID;

/**
 * DTO representing the response from fleet-service.
 * We only include the fields we care about in shipment-service.
 */
public record TruckResponse(
    UUID id,
    String registrationNumber,
    String brand,
    String model,
    Double capacity,
    String status,
    AssignedDriverResponse assignedDriver
) {
    public record AssignedDriverResponse(
        UUID id,
        String matricule,
        String fullName
    ) {
    }
}

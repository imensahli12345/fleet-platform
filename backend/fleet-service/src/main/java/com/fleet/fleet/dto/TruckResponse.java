package com.fleet.fleet.dto;

import com.fleet.fleet.entity.TruckStatus;
import java.util.UUID;

public record TruckResponse(
    UUID id,
    String registrationNumber,
    String brand,
    String model,
    Double capacity,
    TruckStatus status,
    AssignedDriverResponse assignedDriver
) {
    public record AssignedDriverResponse(
        UUID id,
        String matricule,
        String fullName
    ) {
    }
}

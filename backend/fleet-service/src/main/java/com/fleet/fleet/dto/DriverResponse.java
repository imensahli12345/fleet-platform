package com.fleet.fleet.dto;

import java.util.UUID;

public record DriverResponse(
    UUID id,
    UUID authUserId,
    String fullName,
    String matricule,
    String licenseNumber,
    String phoneNumber,
    boolean active
) {
}

package com.fleet.shipment.dto;

import com.fleet.shipment.entity.ShipmentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ShipmentResponse(
        UUID id,
        String origin,
        String destination,
        String customerName,
        UUID assignedTruckId,
        UUID assignedDriverId,
        String assignedTruckRegistration,
        String assignedDriverName,
        ShipmentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

package com.fleet.shipment.dto;

import java.util.UUID;

/** Tells fleet-service whether a truck is currently on a PENDING, IN_TRANSIT or HALTED shipment. */
public record TruckActivityResponse(
        UUID truckId,
        boolean hasActiveShipment
) {}

package com.fleet.shipment.entity;

/**
 * Represents the lifecycle status of a shipment.
 *
 * PENDING    → created but not yet started (truck/driver not yet departed)
 * IN_TRANSIT → the truck is on the road, delivering
 * DELIVERED  → the shipment has been successfully delivered to the customer
 * HALTED     → a HIGH/CRITICAL exception has stopped the delivery (Rule B)
 * CANCELLED  → the shipment was cancelled before delivery
 */
public enum ShipmentStatus {
    PENDING,
    IN_TRANSIT,
    DELIVERED,
    HALTED,
    CANCELLED
}
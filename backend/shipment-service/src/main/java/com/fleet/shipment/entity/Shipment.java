package com.fleet.shipment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The central entity of shipment-service.
 * Represents a delivery order from an origin to a destination.
 *
 * It stores references to the assigned truck and driver by UUID only —
 * there is no JPA @ManyToOne join to fleet-service, because in microservices
 * each service has its own database. Cross-service data is fetched via Feign.
 */
@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private String destination;

    @Column(nullable = false)
    private String customerName;

    // UUID references to fleet-service — no JPA join, intentionally
    private UUID assignedTruckId;
    private UUID assignedDriverId;

    // Denormalized names cached at assignment time (avoid repeated Feign calls)
    private String assignedTruckRegistration;
    private String assignedDriverName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

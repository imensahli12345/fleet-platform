package com.fleet.shipment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents an incident reported during a shipment's delivery
 * (breakdown, customer absent, weather, ...). Analyzed by ai-service
 * from raw text, then structured into severity/category/actionPlan.
 *
 * Not named "Exception" to avoid colliding with java.lang.Exception.
 */
@Entity
@Table(name = "exceptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentException {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    @Builder.Default
    private boolean resolved = false;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawInput;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String actionPlan;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String notificationText;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
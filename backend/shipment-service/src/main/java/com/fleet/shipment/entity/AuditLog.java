package com.fleet.shipment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable trace of every status change (Shipment or ShipmentException).
 * Deliberately decoupled (no @ManyToOne) since it points at either entity
 * depending on entityType — never cascade-deleted with the record it logs.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID exceptionId; // nullable — only set when entityType = "EXCEPTION"

    @Column(nullable = false)
    private String entityType; // "SHIPMENT" or "EXCEPTION"

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private String action; // "CREATE", "STATUS_CHANGE"

    private String oldState;

    @Column(nullable = false)
    private String newState;

    @Column(nullable = false)
    @Builder.Default
    private String changedBy = "Dispatcher_System";

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
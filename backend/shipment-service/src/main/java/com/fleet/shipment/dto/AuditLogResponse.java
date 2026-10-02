package com.fleet.shipment.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID exceptionId,
        String entityType,
        UUID entityId,
        String action,
        String oldState,
        String newState,
        String changedBy,
        LocalDateTime createdAt
) {}

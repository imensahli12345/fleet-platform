package com.fleet.shipment.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on the "incident-events" Kafka topic when a shipment exception is
 * created or resolved. Consumed by notification-service.
 *
 * customerMessage is the AI-written customer notification for INCIDENT_CREATED,
 * and null for INCIDENT_RESOLVED (notification-service writes that message).
 */
public record IncidentEvent(
        UUID eventId,
        IncidentEventType eventType,
        UUID shipmentId,
        UUID exceptionId,
        UUID customerAuthUserId,
        String customerName,
        String severity,
        String category,
        String customerMessage,
        Instant occurredAt) {

    public enum IncidentEventType {
        INCIDENT_CREATED,
        INCIDENT_RESOLVED
    }
}

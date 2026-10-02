package com.fleet.notification.kafka;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumer-side copy of shipment-service's IncidentEvent (incident-events topic).
 * Kept as its own type on purpose: services share the JSON contract, not Java classes.
 */
public record IncidentEvent(
        UUID eventId,
        String eventType,          // INCIDENT_CREATED | INCIDENT_RESOLVED
        UUID shipmentId,
        UUID exceptionId,
        UUID customerAuthUserId,   // null when the shipment has no linked customer account
        String customerName,
        String severity,
        String category,
        String customerMessage,    // AI-written text for INCIDENT_CREATED, null for INCIDENT_RESOLVED
        Instant occurredAt) {
}

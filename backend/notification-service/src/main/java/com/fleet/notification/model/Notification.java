package com.fleet.notification.model;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One customer notification, stored in MongoDB (notification_db.notifications).
 *
 * id = "<exceptionId>:<eventType>", so a Kafka redelivery of the same event maps to the
 * same document and never produces a second notification (idempotent consumer).
 */
@Document(collection = "notifications")
public record Notification(
        @Id String id,
        String eventType,
        @Indexed UUID shipmentId,
        UUID exceptionId,
        @Indexed UUID customerAuthUserId,
        String customerName,
        String severity,
        String category,
        String message,
        String channel,           // LOG for now (no real email/SMS provider)
        NotificationStatus status,
        Instant occurredAt,
        Instant createdAt) {

    public enum NotificationStatus {
        /** "Sent" through the LOG channel to the shipment's linked customer. */
        SENT,
        /** The shipment has no linked customer account, so nobody could be notified. */
        NO_RECIPIENT
    }
}

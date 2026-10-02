package com.fleet.notification.service;

import com.fleet.notification.kafka.IncidentEvent;
import com.fleet.notification.model.Notification;
import com.fleet.notification.model.Notification.NotificationStatus;
import com.fleet.notification.repository.NotificationRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    static final String RESOLVED_MESSAGE =
            "The issue affecting your delivery has been resolved. Your shipment is back on its way.";
    static final String CHANNEL = "LOG";

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public void handle(IncidentEvent event) {
        String id = event.exceptionId() + ":" + event.eventType();
        if (repository.existsById(id)) {
            // Kafka is at-least-once: the same event can arrive twice. Never notify twice.
            log.info("Duplicate {} for exception={} ignored (already notified)", event.eventType(), event.exceptionId());
            return;
        }

        String message = "INCIDENT_RESOLVED".equals(event.eventType()) ? RESOLVED_MESSAGE : event.customerMessage();
        NotificationStatus status = event.customerAuthUserId() == null ? NotificationStatus.NO_RECIPIENT : NotificationStatus.SENT;

        if (status == NotificationStatus.SENT) {
            // The LOG channel stands in for a real email/SMS provider.
            log.info("NOTIFY customer={} ({}) shipment={}: {}",
                    event.customerAuthUserId(), event.customerName(), event.shipmentId(), message);
        } else {
            log.warn("No customer account linked to shipment={} -- {} notification stored as NO_RECIPIENT",
                    event.shipmentId(), event.eventType());
        }

        repository.save(new Notification(
                id,
                event.eventType(),
                event.shipmentId(),
                event.exceptionId(),
                event.customerAuthUserId(),
                event.customerName(),
                event.severity(),
                event.category(),
                message,
                CHANNEL,
                status,
                event.occurredAt(),
                Instant.now()));
    }
}

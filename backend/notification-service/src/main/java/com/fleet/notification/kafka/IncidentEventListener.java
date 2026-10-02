package com.fleet.notification.kafka;

import com.fleet.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class IncidentEventListener {

    private static final Logger log = LoggerFactory.getLogger(IncidentEventListener.class);

    private final NotificationService notificationService;

    public IncidentEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${app.kafka.topics.incident-events:incident-events}")
    public void onIncidentEvent(IncidentEvent event) {
        log.info("Received {} exception={} shipment={}", event.eventType(), event.exceptionId(), event.shipmentId());
        notificationService.handle(event);
    }
}

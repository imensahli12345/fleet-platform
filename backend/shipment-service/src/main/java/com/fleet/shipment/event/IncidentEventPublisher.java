package com.fleet.shipment.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends IncidentEvents to Kafka, but only once the database transaction that
 * produced them has committed:
 *
 *  1. ShipmentService saves the exception and calls publishEvent(...) inside
 *     its @Transactional method -- nothing is sent yet.
 *  2. If the transaction commits, Spring invokes this listener (AFTER_COMMIT).
 *  3. If the transaction rolls back, this listener is never invoked, so no
 *     notification can exist for an incident that was never persisted.
 *  4. If Kafka is unavailable after the commit, the failure is logged and
 *     swallowed: the incident stays saved and the API call still succeeds.
 */
@Component
public class IncidentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(IncidentEventPublisher.class);

    private final KafkaTemplate<String, IncidentEvent> kafkaTemplate;
    private final String topic;

    public IncidentEventPublisher(KafkaTemplate<String, IncidentEvent> kafkaTemplate,
                                  @Value("${app.kafka.topics.incident-events:incident-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(IncidentEvent event) {
        // Keyed by shipment so all events of one shipment stay ordered on one partition.
        String key = event.shipmentId().toString();
        try {
            kafkaTemplate.send(topic, key, event).whenComplete((result, failure) -> {
                if (failure != null) {
                    log.error("Kafka publish FAILED after commit: {} exception={} shipment={} -- incident remains saved",
                            event.eventType(), event.exceptionId(), event.shipmentId(), failure);
                } else {
                    log.info("Kafka published {} exception={} shipment={} partition={} offset={}",
                            event.eventType(), event.exceptionId(), event.shipmentId(),
                            result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                }
            });
        } catch (RuntimeException failure) {
            // send() itself can throw (e.g. broker unreachable past max.block.ms). An exception
            // escaping an AFTER_COMMIT listener would reach the caller after the data is already
            // committed, so it must never propagate.
            log.error("Kafka publish FAILED after commit: {} exception={} shipment={} -- incident remains saved",
                    event.eventType(), event.exceptionId(), event.shipmentId(), failure);
        }
    }
}

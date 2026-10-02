package com.fleet.shipment.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fleet.shipment.event.IncidentEvent.IncidentEventType;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the publish-after-commit contract of IncidentEventPublisher with a real Spring
 * transaction lifecycle and a mocked KafkaTemplate:
 *  - committed transaction  -> event sent to Kafka once
 *  - rolled-back transaction -> event never sent
 *  - exception in the transaction -> event never sent
 *  - Kafka failure after commit -> nothing propagates to the caller
 *  - no transaction at all -> event never sent (no accidental "send immediately")
 */
@SpringJUnitConfig(IncidentEventPublisherTransactionTest.Config.class)
class IncidentEventPublisherTransactionTest {

    // @EnableTransactionManagement is what makes @TransactionalEventListener wait for the commit
    // (it registers TransactionalEventListenerFactory). Spring Boot enables it in the real app via
    // TransactionAutoConfiguration -- the same switch the service's @Transactional methods rely on.
    // Without it the listener would fire immediately, which is exactly what this test guards against.
    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, IncidentEvent> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }

        @Bean
        IncidentEventPublisher incidentEventPublisher(KafkaTemplate<String, IncidentEvent> kafkaTemplate) {
            return new IncidentEventPublisher(kafkaTemplate, "incident-events");
        }

        /** Minimal in-memory transaction manager: real begin/commit/rollback lifecycle, no database. */
        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private KafkaTemplate<String, IncidentEvent> kafkaTemplate;

    @Autowired
    private TransactionTemplate transaction;

    @BeforeEach
    void resetKafka() {
        reset(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), anyString(), any(IncidentEvent.class)))
                .thenReturn(new CompletableFuture<>());
    }

    @Test
    void committedTransactionPublishesOnce() {
        IncidentEvent event = event();

        transaction.executeWithoutResult(status -> events.publishEvent(event));

        verify(kafkaTemplate, times(1)).send(eq("incident-events"), eq(event.shipmentId().toString()), eq(event));
    }

    @Test
    void nothingIsSentBeforeTheCommit() {
        transaction.executeWithoutResult(status -> {
            events.publishEvent(event());
            // Still inside the transaction: the event must only be queued.
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any(IncidentEvent.class));
        });
    }

    @Test
    void rolledBackTransactionNeverPublishes() {
        transaction.executeWithoutResult(status -> {
            events.publishEvent(event());
            status.setRollbackOnly();
        });

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(IncidentEvent.class));
    }

    @Test
    void exceptionInTransactionNeverPublishes() {
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            events.publishEvent(event());
            throw new IllegalStateException("simulated failure after the save");
        }));

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(IncidentEvent.class));
    }

    @Test
    void kafkaFailureAfterCommitDoesNotReachTheCaller() {
        when(kafkaTemplate.send(anyString(), anyString(), any(IncidentEvent.class)))
                .thenThrow(new KafkaException("broker unavailable"));

        assertDoesNotThrow(() -> transaction.executeWithoutResult(status -> events.publishEvent(event())));

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any(IncidentEvent.class));
    }

    @Test
    void asyncKafkaFailureAfterCommitDoesNotReachTheCaller() {
        when(kafkaTemplate.send(anyString(), anyString(), any(IncidentEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("delivery timeout")));

        assertDoesNotThrow(() -> transaction.executeWithoutResult(status -> events.publishEvent(event())));
    }

    @Test
    void eventOutsideAnyTransactionIsNotPublished() {
        events.publishEvent(event());

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(IncidentEvent.class));
    }

    private static IncidentEvent event() {
        return new IncidentEvent(UUID.randomUUID(), IncidentEventType.INCIDENT_CREATED, UUID.randomUUID(),
                UUID.randomUUID(), null, "Test Customer", "LOW", "CUSTOMER_ABSENT",
                "Your delivery may be delayed.", Instant.now());
    }
}

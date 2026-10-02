package com.fleet.notification.repository;

import com.fleet.notification.model.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByShipmentIdOrderByCreatedAtDesc(UUID shipmentId);

    List<Notification> findByCustomerAuthUserIdOrderByCreatedAtDesc(UUID customerAuthUserId);

    List<Notification> findByShipmentIdAndCustomerAuthUserIdOrderByCreatedAtDesc(UUID shipmentId, UUID customerAuthUserId);

    List<Notification> findAllByOrderByCreatedAtDesc();
}

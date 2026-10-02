package com.fleet.notification.controller;

import com.fleet.notification.model.Notification;
import com.fleet.notification.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * DISPATCHER / ADMIN see every notification (optionally for one shipment).
     * CUSTOMER sees only their own: the customer id always comes from the JWT subject,
     * never from a request parameter.
     */
    @PreAuthorize("hasRole('DISPATCHER') or hasRole('ADMIN') or hasRole('CUSTOMER')")
    @GetMapping
    public List<Notification> list(@RequestParam(name = "shipmentId", required = false) UUID shipmentId,
                                   Authentication authentication) {
        if (isStaff(authentication)) {
            return shipmentId != null
                    ? repository.findByShipmentIdOrderByCreatedAtDesc(shipmentId)
                    : repository.findAllByOrderByCreatedAtDesc();
        }
        UUID customerId = UUID.fromString(authentication.getName());
        return shipmentId != null
                ? repository.findByShipmentIdAndCustomerAuthUserIdOrderByCreatedAtDesc(shipmentId, customerId)
                : repository.findByCustomerAuthUserIdOrderByCreatedAtDesc(customerId);
    }

    private static boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_DISPATCHER")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
    }
}

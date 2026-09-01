package com.fleet.shipment.repository;

import com.fleet.shipment.entity.Shipment;
import com.fleet.shipment.entity.ShipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    List<Shipment> findByStatus(ShipmentStatus status);
    List<Shipment> findByAssignedTruckId(UUID assignedTruckId);
}

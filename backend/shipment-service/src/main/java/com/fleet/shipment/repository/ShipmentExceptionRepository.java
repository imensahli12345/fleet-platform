package com.fleet.shipment.repository;

import com.fleet.shipment.entity.ShipmentException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShipmentExceptionRepository extends JpaRepository<ShipmentException, UUID> {

    boolean existsByShipmentIdAndResolvedFalse(UUID shipmentId);

    List<ShipmentException> findByShipmentId(UUID shipmentId);
}
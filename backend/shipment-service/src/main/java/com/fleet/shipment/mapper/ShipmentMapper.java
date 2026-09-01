package com.fleet.shipment.mapper;

import com.fleet.shipment.dto.ShipmentResponse;
import com.fleet.shipment.entity.Shipment;
import org.springframework.stereotype.Component;

@Component
public class ShipmentMapper {

    public ShipmentResponse toResponse(Shipment shipment) {
        if (shipment == null) {
            return null;
        }

        return new ShipmentResponse(
            shipment.getId(),
            shipment.getOrigin(),
            shipment.getDestination(),
            shipment.getCustomerName(),
            shipment.getAssignedTruckId(),
            shipment.getAssignedDriverId(),
            shipment.getAssignedTruckRegistration(),
            shipment.getAssignedDriverName(),
            shipment.getStatus(),
            shipment.getCreatedAt(),
            shipment.getUpdatedAt()
        );
    }
}

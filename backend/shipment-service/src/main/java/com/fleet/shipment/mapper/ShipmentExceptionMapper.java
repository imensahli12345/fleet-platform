package com.fleet.shipment.mapper;

import com.fleet.shipment.dto.ShipmentExceptionResponse;
import com.fleet.shipment.entity.ShipmentException;
import org.springframework.stereotype.Component;

@Component
public class ShipmentExceptionMapper {

    public ShipmentExceptionResponse toResponse(ShipmentException exception) {
        return new ShipmentExceptionResponse(
                exception.getId(),
                exception.getShipment().getId(),
                exception.getSeverity(),
                exception.getCategory(),
                exception.isResolved(),
                exception.getRawInput(),
                exception.getActionPlan(),
                exception.getNotificationText(),
                exception.getCreatedAt()
        );
    }
}
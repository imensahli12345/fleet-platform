package com.fleet.fleet.mapper;

import com.fleet.fleet.dto.DriverResponse;
import com.fleet.fleet.dto.TruckResponse;
import com.fleet.fleet.entity.Driver;
import com.fleet.fleet.entity.Truck;
import org.springframework.stereotype.Component;

@Component
public class FleetMapper {

    public DriverResponse toDriverResponse(Driver driver) {
        return new DriverResponse(
            driver.getId(),
            driver.getAuthUserId(),
            driver.getFullName(),
            driver.getMatricule(),
            driver.getLicenseNumber(),
            driver.getPhoneNumber(),
            driver.isActive()
        );
    }

    public TruckResponse toTruckResponse(Truck truck) {
        TruckResponse.AssignedDriverResponse assignedDriver = null;
        if (truck.getAssignedDriver() != null) {
            assignedDriver = new TruckResponse.AssignedDriverResponse(
                truck.getAssignedDriver().getId(),
                truck.getAssignedDriver().getMatricule(),
                truck.getAssignedDriver().getFullName()
            );
        }

        return new TruckResponse(
            truck.getId(),
            truck.getRegistrationNumber(),
            truck.getBrand(),
            truck.getModel(),
            truck.getCapacity(),
            truck.getStatus(),
            assignedDriver
        );
    }
}

package com.fleet.shipment.service;

import com.fleet.shipment.client.FleetServiceClient;
import com.fleet.shipment.client.dto.TruckResponse;
import com.fleet.shipment.dto.CreateShipmentRequest;
import com.fleet.shipment.dto.ShipmentResponse;
import com.fleet.shipment.entity.Shipment;
import com.fleet.shipment.entity.ShipmentStatus;
import com.fleet.shipment.mapper.ShipmentMapper;
import com.fleet.shipment.repository.ShipmentRepository;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final FleetServiceClient fleetServiceClient;
    private final ShipmentMapper shipmentMapper;

    public ShipmentService(ShipmentRepository shipmentRepository, FleetServiceClient fleetServiceClient, ShipmentMapper shipmentMapper) {
        this.shipmentRepository = shipmentRepository;
        this.fleetServiceClient = fleetServiceClient;
        this.shipmentMapper = shipmentMapper;
    }

    @Transactional
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        TruckResponse assignedTruck = null;

        try {
            if (request.assignedTruckId() != null) {
                // If dispatcher provided a specific truck, validate it
                assignedTruck = fleetServiceClient.getTruckById(request.assignedTruckId());
                if (!"ASSIGNED".equals(assignedTruck.status())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The requested truck must be ASSIGNED to a driver before it can take a shipment");
                }
            } else {
                // Auto-assign the first available truck
                List<TruckResponse> availableTrucks = fleetServiceClient.getAvailableTrucks();
                if (availableTrucks.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "No available trucks found in the fleet");
                }
                assignedTruck = availableTrucks.get(0);
            }
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Requested truck not found in fleet-service");
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not communicate with fleet-service to assign truck");
        }

        // We assign the truck to the driver immediately for the shipment (in fleet service)
        // Wait, normally the truck is already assigned to a driver, or we just assign the truck to the shipment.
        // For this demo, let's just assume the truck has an assigned driver, and we lock them into IN_TRANSIT.
        if (assignedTruck.assignedDriver() == null || assignedTruck.assignedDriver().id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected truck has no assigned driver");
        }

        Shipment shipment = Shipment.builder()
                .origin(request.origin())
                .destination(request.destination())
                .customerName(request.customerName())
                .assignedTruckId(assignedTruck.id())
                .assignedDriverId(assignedTruck.assignedDriver().id())
                .assignedTruckRegistration(assignedTruck.registrationNumber())
                .assignedDriverName(assignedTruck.assignedDriver().fullName())
                .status(ShipmentStatus.IN_TRANSIT) // Assuming it departs immediately
                .build();

        return shipmentMapper.toResponse(shipmentRepository.save(shipment));
    }

    @Transactional(readOnly = true)
    public List<ShipmentResponse> getAllShipments() {
        return shipmentRepository.findAll().stream()
                .map(shipmentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentById(UUID id) {
        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
        return shipmentMapper.toResponse(shipment);
    }
}

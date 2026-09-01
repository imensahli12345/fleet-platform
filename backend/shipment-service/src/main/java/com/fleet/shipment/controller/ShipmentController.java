package com.fleet.shipment.controller;

import com.fleet.shipment.dto.CreateShipmentRequest;
import com.fleet.shipment.dto.ShipmentResponse;
import com.fleet.shipment.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @Operation(summary = "Create a new shipment and assign a truck via fleet-service")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentResponse createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        return shipmentService.createShipment(request);
    }

    @Operation(summary = "Get all shipments")
    @GetMapping
    public List<ShipmentResponse> getAllShipments() {
        return shipmentService.getAllShipments();
    }

    @Operation(summary = "Get a shipment by ID")
    @GetMapping("/{id}")
    public ShipmentResponse getShipmentById(@PathVariable("id") UUID id) {
        return shipmentService.getShipmentById(id);
    }
}

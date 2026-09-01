package com.fleet.shipment.client;

import com.fleet.shipment.client.dto.TruckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

/**
 * OpenFeign declarative HTTP client for fleet-service.
 *
 * How it works:
 * 1. @FeignClient(name = "fleet-service") tells Spring Cloud to look up "fleet-service"
 *    in Eureka by that exact service name (spring.application.name in fleet-service).
 * 2. Spring Cloud LoadBalancer picks one of the registered instances automatically.
 * 3. Each method maps to an HTTP endpoint on fleet-service, exactly like @RestController.
 * 4. Feign serializes/deserializes JSON automatically (using Jackson under the hood).
 * 5. If fleet-service is down, Feign throws a FeignException which we catch in ShipmentService.
 */
@FeignClient(name = "fleet-service")
//Create an HTTP client for me that communicates with fleet-service
//
public interface FleetServiceClient {

    /**
     * Calls GET /api/fleet/trucks/available on fleet-service.
     * Returns all trucks with status AVAILABLE.
     * Used when auto-assigning a truck to a new shipment.
     */
    @GetMapping("/api/fleet/trucks/available")
    List<TruckResponse> getAvailableTrucks();

    /**
     * Calls GET /api/fleet/trucks/{truckId} on fleet-service.
     * Used to validate a manually provided truck ID and fetch its details.
     */
    @GetMapping("/api/fleet/trucks/{truckId}")
    TruckResponse getTruckById(@PathVariable("truckId") UUID truckId);

    /**
     * Calls POST /api/fleet/trucks/{truckId}/assign on fleet-service.
     * Assigns a driver to a truck and marks it as ASSIGNED.
     * Used when starting a shipment (transitioning to IN_TRANSIT).
     */
    @PostMapping("/api/fleet/trucks/{truckId}/assign")
    TruckResponse assignDriverToTruck(
            @PathVariable("truckId") UUID truckId,
            @RequestBody AssignRequest request
    );

    /** Inner record used as the request body for the assign call */
    record AssignRequest(UUID driverId) {}
}

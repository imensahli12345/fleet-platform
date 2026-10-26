package com.fleet.fleet.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * OpenFeign client for shipment-service, resolved by name through Eureka.
 * The caller's JWT is forwarded by FeignClientInterceptor.
 */
@FeignClient(name = "shipment-service")
public interface ShipmentServiceClient {

    /** Calls GET /api/shipments/trucks/{truckId}/active on shipment-service. */
    @GetMapping("/api/shipments/trucks/{truckId}/active")
    TruckActivityResponse getTruckActivity(@PathVariable("truckId") UUID truckId);

    record TruckActivityResponse(UUID truckId, boolean hasActiveShipment) {}
}

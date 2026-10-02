package com.fleet.shipment.client;

import com.fleet.shipment.client.dto.TruckResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Circuit-breaker-protected access to fleet-service. ShipmentService calls this
 * instead of FleetServiceClient directly.
 *
 * All methods share ONE breaker ("fleet-service", configured in config-repo/shipment-service.yml):
 *  - CLOSED: calls go through; outages (connection refused, timeout, 5xx) are counted.
 *  - OPEN: once too many recent calls failed, calls are rejected instantly with
 *    CallNotPermittedException -- no network call, no waiting on a dead service.
 *  - HALF_OPEN: after the wait, a few trial calls decide between CLOSED and OPEN again.
 *
 * Business answers such as 404 "truck not found" are NOT failures: fleet-service
 * answered correctly, so they never trip the breaker (see record-exceptions).
 *
 * No fallbackMethod on purpose: there is no safe substitute for a truck or a driver
 * identity, so the caller turns an open breaker into a fast 503 (fail closed).
 */
@Component
public class FleetGateway {

    static final String BREAKER = "fleet-service";

    private final FleetServiceClient client;

    public FleetGateway(FleetServiceClient client) {
        this.client = client;
    }

    @CircuitBreaker(name = BREAKER)
    public List<TruckResponse> getAvailableTrucks() {
        return client.getAvailableTrucks();
    }

    @CircuitBreaker(name = BREAKER)
    public TruckResponse getTruckById(UUID truckId) {
        return client.getTruckById(truckId);
    }

    @CircuitBreaker(name = BREAKER)
    public FleetServiceClient.DriverResponse getDriverById(UUID driverId) {
        return client.getDriverById(driverId);
    }
}

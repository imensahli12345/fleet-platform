package com.fleet.fleet.controller;

import com.fleet.fleet.dto.AssignTruckRequest;
import com.fleet.fleet.dto.CreateDriverRequest;
import com.fleet.fleet.dto.CreateTruckRequest;
import com.fleet.fleet.dto.DriverResponse;
import com.fleet.fleet.dto.TruckResponse;
import com.fleet.fleet.dto.UpdateDriverActivationRequest;
import com.fleet.fleet.dto.UpdateMyDriverProfileRequest;
import com.fleet.fleet.service.FleetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/fleet")
public class FleetController {

    private final FleetService fleetService;

    public FleetController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    // ──────────────── DRIVER ENDPOINTS ────────────────

    @Operation(summary = "Create a new driver")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/drivers")
    @ResponseStatus(HttpStatus.CREATED)
    public DriverResponse createDriver(@Valid @RequestBody CreateDriverRequest request) {
        return fleetService.createDriver(request);
    }

    @Operation(summary = "Complete the authenticated driver's profile")

@PreAuthorize("hasRole('DRIVER')")
    @PatchMapping("/drivers/me")
    public DriverResponse updateMyDriverProfile(
        @Valid @RequestBody UpdateMyDriverProfileRequest request,
        Principal principal
    ) {
        return fleetService.updateMyDriverProfile(extractAuthUserId(principal), request);
    }

    @Operation(summary = "Activate or deactivate a driver")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/drivers/{driverId}/activation")
    public DriverResponse updateDriverActivation(
        @Parameter(description = "UUID of the driver", required = true)
        @PathVariable("driverId") UUID driverId,
        @Valid @RequestBody UpdateDriverActivationRequest request
    ) {
        return fleetService.updateDriverActivation(driverId, request);
    }

    @Operation(summary = "Get all drivers")
    @GetMapping("/drivers")
    public List<DriverResponse> getAllDrivers() {
        return fleetService.getAllDrivers();
    }

    @Operation(summary = "Get a driver by ID")
    @GetMapping("/drivers/{driverId}")
    public DriverResponse getDriverById(
        @Parameter(description = "UUID of the driver", required = true)
        @PathVariable("driverId") UUID driverId
    ) {
        return fleetService.getDriverById(driverId);
    }

    @Operation(summary = "Delete a driver (only if not assigned to a truck)")
    @DeleteMapping("/drivers/{driverId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDriver(
        @Parameter(description = "UUID of the driver", required = true)
        @PathVariable("driverId") UUID driverId
    ) {
        fleetService.deleteDriver(driverId);
    }

    // ──────────────── TRUCK ENDPOINTS ────────────────

    @Operation(summary = "Create a new truck")
    @PostMapping("/trucks")
    @ResponseStatus(HttpStatus.CREATED)
    public TruckResponse createTruck(@Valid @RequestBody CreateTruckRequest request) {
        return fleetService.createTruck(request);
    }

    @Operation(summary = "Get all trucks")
    @GetMapping("/trucks")
    public List<TruckResponse> getAllTrucks() {
        return fleetService.getAllTrucks();
    }

    @Operation(summary = "Get a truck by ID")
    @GetMapping("/trucks/{truckId}")
    public TruckResponse getTruckById(
        @Parameter(description = "UUID of the truck", required = true)
        @PathVariable("truckId") UUID truckId
    ) {
        return fleetService.getTruckById(truckId);
    }

    @Operation(summary = "Get all available trucks")
    @GetMapping("/trucks/available")
    public List<TruckResponse> getAvailableTrucks() {
        return fleetService.getAvailableTrucks();
    }

    @Operation(summary = "Delete a truck (only if not assigned)")
    @DeleteMapping("/trucks/{truckId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTruck(
        @Parameter(description = "UUID of the truck", required = true)
        @PathVariable("truckId") UUID truckId
    ) {
        fleetService.deleteTruck(truckId);
    }

    @Operation(summary = "Assign a driver to a truck")
    @PostMapping("/trucks/{truckId}/assign")
    public TruckResponse assignDriverToTruck(
        @Parameter(description = "UUID of the truck", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @PathVariable("truckId") UUID truckId,
        @Valid @RequestBody AssignTruckRequest request
    ) {
        return fleetService.assignDriverToTruck(truckId, request);
    }

    private UUID extractAuthUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing authenticated user");
        }

        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid authenticated user id");
        }
    }
}

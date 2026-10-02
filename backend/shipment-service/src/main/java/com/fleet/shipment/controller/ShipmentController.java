package com.fleet.shipment.controller;

import com.fleet.shipment.dto.AnalyzeExceptionRequest;
import com.fleet.shipment.dto.AuditLogResponse;
import com.fleet.shipment.dto.CreateShipmentRequest;
import com.fleet.shipment.dto.ExceptionAnalysisResult;
import com.fleet.shipment.dto.ShipmentExceptionResponse;
import com.fleet.shipment.dto.ShipmentResponse;
import com.fleet.shipment.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.security.Principal;

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

    @Operation(summary = "Analyze a reported shipment exception with ai-service")
    @PreAuthorize("hasRole('DISPATCHER')")
    @PostMapping("/{id}/exceptions/analyze")
    public ExceptionAnalysisResult analyzeException(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AnalyzeExceptionRequest request) {
        return shipmentService.analyzeAndCreateException(id, request.rawText());
    }

    @Operation(summary = "Resolve a shipment exception")
    @PreAuthorize("hasRole('DISPATCHER') or hasRole('ADMIN')")
    @PatchMapping("/exceptions/{exceptionId}/resolve")
    public ShipmentExceptionResponse resolveException(@PathVariable("exceptionId") UUID exceptionId) {
        return shipmentService.resolveException(exceptionId);
    }

    @Operation(summary = "Mark a shipment as delivered")
    @PreAuthorize("hasRole('DISPATCHER') or hasRole('ADMIN') or hasRole('DRIVER')")
    @PatchMapping("/{id}/deliver")
    public ShipmentResponse markDelivered(
            @PathVariable("id") UUID id,
            Principal principal,
            Authentication authentication) {
        if (!hasDispatchAuthority(authentication)) {
            shipmentService.assertDriverOwnsShipment(id, extractAuthUserId(principal));
        }
        return shipmentService.markDelivered(id);
    }

    @Operation(summary = "Get exceptions reported for a shipment")
    @PreAuthorize("hasRole('DISPATCHER') or hasRole('ADMIN') or hasRole('CUSTOMER')")
    @GetMapping("/{id}/exceptions")
    public List<ShipmentExceptionResponse> getExceptions(
            @PathVariable("id") UUID id,
            Principal principal,
            Authentication authentication) {
        if (!hasDispatchAuthority(authentication)) {
            shipmentService.assertCustomerOwnsShipment(id, extractAuthUserId(principal));
        }
        return shipmentService.getExceptionsForShipment(id);
    }

    @Operation(summary = "Get audit logs for a shipment")
    @PreAuthorize("hasRole('DISPATCHER') or hasRole('ADMIN')")
    @GetMapping("/{id}/audit-logs")
    public List<AuditLogResponse> getAuditLogs(@PathVariable("id") UUID id) {
        return shipmentService.getAuditLogsForShipment(id);
    }

    private boolean hasDispatchAuthority(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_DISPATCHER")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private UUID extractAuthUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing authenticated user");
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid authenticated user id");
        }
    }
}

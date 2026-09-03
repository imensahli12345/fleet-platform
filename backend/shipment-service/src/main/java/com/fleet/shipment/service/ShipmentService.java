package com.fleet.shipment.service;

import com.fleet.shipment.client.AiServiceClient;
import com.fleet.shipment.client.FleetServiceClient;
import com.fleet.shipment.client.dto.TruckResponse;
import com.fleet.shipment.dto.CreateShipmentRequest;
import com.fleet.shipment.dto.ExceptionAnalysisResult;
import com.fleet.shipment.dto.ShipmentExceptionResponse;
import com.fleet.shipment.dto.ShipmentResponse;
import com.fleet.shipment.entity.AuditLog;
import com.fleet.shipment.entity.Category;
import com.fleet.shipment.entity.Severity;
import com.fleet.shipment.entity.Shipment;
import com.fleet.shipment.entity.ShipmentException;
import com.fleet.shipment.entity.ShipmentStatus;
import com.fleet.shipment.exception.AiAnalysisFailedException;
import com.fleet.shipment.mapper.ShipmentExceptionMapper;
import com.fleet.shipment.mapper.ShipmentMapper;
import com.fleet.shipment.repository.AuditLogRepository;
import com.fleet.shipment.repository.ShipmentExceptionRepository;
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
    private final ShipmentExceptionRepository exceptionRepository;
    private final AuditLogRepository auditLogRepository;
    private final FleetServiceClient fleetServiceClient;
    private final AiServiceClient aiServiceClient;
    private final ShipmentMapper shipmentMapper;
    private final ShipmentExceptionMapper exceptionMapper;

    public ShipmentService(ShipmentRepository shipmentRepository,
                           ShipmentExceptionRepository exceptionRepository,
                           AuditLogRepository auditLogRepository,
                           FleetServiceClient fleetServiceClient,
                           AiServiceClient aiServiceClient,
                           ShipmentMapper shipmentMapper,
                           ShipmentExceptionMapper exceptionMapper) {
        this.shipmentRepository = shipmentRepository;
        this.exceptionRepository = exceptionRepository;
        this.auditLogRepository = auditLogRepository;
        this.fleetServiceClient = fleetServiceClient;
        this.aiServiceClient = aiServiceClient;
        this.shipmentMapper = shipmentMapper;
        this.exceptionMapper = exceptionMapper;
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
    @Transactional
    public ExceptionAnalysisResult analyzeAndCreateException(UUID shipmentId, String rawInput) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));

        AiServiceClient.AnalyzeResponse aiResult;
        try {
            aiResult = aiServiceClient.analyze(rawInput, shipmentId);
        } catch (AiAnalysisFailedException e) {
            return ExceptionAnalysisResult.fallbackMode(e.getMessage());
        }

        Severity severity;
        Category category;
        try {
            severity = Severity.valueOf(aiResult.structuredRecord().severity());
            category = Category.valueOf(aiResult.structuredRecord().category());
        } catch (IllegalArgumentException e) {
            // ai-service returned a value outside the taxonomy — treat as failure, don't guess
            return ExceptionAnalysisResult.fallbackMode("AI returned an unrecognized severity/category");
        }

        ShipmentException exception = ShipmentException.builder()
                .shipment(shipment)
                .severity(severity)
                .category(category)
                .rawInput(rawInput)
                .actionPlan(aiResult.actionPlan())
                .notificationText(aiResult.customerNotification())
                .build();
        exceptionRepository.save(exception);

        auditLogRepository.save(AuditLog.builder()
                .entityType("EXCEPTION")
                .entityId(exception.getId())
                .action("CREATE")
                .oldState(null)
                .newState("OPEN")
                .build());

        // Rule B — HIGH/CRITICAL forces the shipment to HALTED
        if (severity == Severity.HIGH || severity == Severity.CRITICAL) {
            String oldStatus = shipment.getStatus().name();
            shipment.setStatus(ShipmentStatus.HALTED);
            shipmentRepository.save(shipment);

            auditLogRepository.save(AuditLog.builder()
                    .entityType("SHIPMENT")
                    .entityId(shipmentId)
                    .action("STATUS_CHANGE")
                    .oldState(oldStatus)
                    .newState("HALTED")
                    .build());
        }

        return ExceptionAnalysisResult.success(exceptionMapper.toResponse(exception));
    }

    /**
     * Marks an exception resolved. If it was the shipment's last open
     * exception and the shipment was HALTED, brings it back to IN_TRANSIT.
     */
    @Transactional
    public ShipmentExceptionResponse resolveException(UUID exceptionId) {
        ShipmentException exception = exceptionRepository.findById(exceptionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exception not found"));

        if (exception.isResolved()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exception is already resolved");
        }

        exception.setResolved(true);
        exceptionRepository.save(exception);

        auditLogRepository.save(AuditLog.builder()
                .entityType("EXCEPTION")
                .entityId(exceptionId)
                .action("STATUS_CHANGE")
                .oldState("OPEN")
                .newState("RESOLVED")
                .build());

        Shipment shipment = exception.getShipment();
        boolean stillHasOpenExceptions =
                exceptionRepository.existsByShipmentIdAndResolvedFalse(shipment.getId());

        if (!stillHasOpenExceptions && shipment.getStatus() == ShipmentStatus.HALTED) {
            shipment.setStatus(ShipmentStatus.IN_TRANSIT);
            shipmentRepository.save(shipment);

            auditLogRepository.save(AuditLog.builder()
                    .entityType("SHIPMENT")
                    .entityId(shipment.getId())
                    .action("STATUS_CHANGE")
                    .oldState("HALTED")
                    .newState("IN_TRANSIT")
                    .build());
        }

        return exceptionMapper.toResponse(exception);
    }

    /**
     * Rule A — a shipment cannot be marked DELIVERED while it has any
     * unresolved exception.
     */
    @Transactional
    public ShipmentResponse markDelivered(UUID shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));

        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipment is already delivered");
        }

        boolean hasOpenExceptions =
                exceptionRepository.existsByShipmentIdAndResolvedFalse(shipmentId);
        if (hasOpenExceptions) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot deliver: one or more exceptions must be resolved first");
        }

        String oldStatus = shipment.getStatus().name();
        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipmentRepository.save(shipment);

        auditLogRepository.save(AuditLog.builder()
                .entityType("SHIPMENT")
                .entityId(shipmentId)
                .action("STATUS_CHANGE")
                .oldState(oldStatus)
                .newState("DELIVERED")
                .build());

        return shipmentMapper.toResponse(shipment);
    }

    @Transactional(readOnly = true)
    public List<ShipmentExceptionResponse> getExceptionsForShipment(UUID shipmentId) {
        return exceptionRepository.findByShipmentId(shipmentId).stream()
                .map(exceptionMapper::toResponse)
                .toList();
    }
}


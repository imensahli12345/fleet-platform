package com.fleet.fleet.service;

import com.fleet.fleet.dto.AssignTruckRequest;
import com.fleet.fleet.dto.CreateDriverRequest;
import com.fleet.fleet.dto.CreateTruckRequest;
import com.fleet.fleet.dto.DriverResponse;
import com.fleet.fleet.dto.TruckResponse;
import com.fleet.fleet.dto.UpdateDriverActivationRequest;
import com.fleet.fleet.dto.UpdateMyDriverProfileRequest;
import com.fleet.fleet.entity.Driver;
import com.fleet.fleet.entity.Truck;
import com.fleet.fleet.entity.TruckStatus;
import com.fleet.fleet.mapper.FleetMapper;
import com.fleet.fleet.repository.DriverRepository;
import com.fleet.fleet.repository.TruckRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FleetService {

    private final DriverRepository driverRepository;
    private final TruckRepository truckRepository;
    private final FleetMapper fleetMapper;

    public FleetService(DriverRepository driverRepository, TruckRepository truckRepository, FleetMapper fleetMapper) {
        this.driverRepository = driverRepository;
        this.truckRepository = truckRepository;
        this.fleetMapper = fleetMapper;
    }

    @Transactional
    public DriverResponse createDriver(CreateDriverRequest request) {
        driverRepository.findByAuthUserId(request.authUserId()).ifPresent(driver -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "auth user already linked to a driver");
        });

        String normalizedMatricule = normalizeUpper(request.matricule());
        if (driverRepository.existsByMatricule(normalizedMatricule)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "matricule already used");
        }

        Driver driver = Driver.builder()
            .authUserId(request.authUserId())
            .fullName(request.fullName().trim())
            .matricule(normalizedMatricule)
            .licenseNumber(null)
            .phoneNumber(null)
            .active(false)
            .build();

        return fleetMapper.toDriverResponse(driverRepository.save(driver));
    }

    @Transactional
    public DriverResponse updateMyDriverProfile(UUID authUserId, UpdateMyDriverProfileRequest request) {
        Driver driver = driverRepository.findByAuthUserId(authUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "driver not found"));

        driver.setLicenseNumber(normalizeUpper(request.licenseNumber()));
        driver.setPhoneNumber(normalizePhoneNumber(request.phoneNumber()));

        return fleetMapper.toDriverResponse(driverRepository.save(driver));
    }

    @Transactional
    public DriverResponse updateDriverActivation(UUID driverId, UpdateDriverActivationRequest request) {
        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "driver not found"));

        if (Boolean.TRUE.equals(request.active()) && !isDriverProfileComplete(driver)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "driver profile is incomplete: licenseNumber and phoneNumber are required before activation"
            );
        }

        driver.setActive(request.active());
        return fleetMapper.toDriverResponse(driverRepository.save(driver));
    }

    @Transactional
    public TruckResponse createTruck(CreateTruckRequest request) {
        String normalizedRegistrationNumber = normalizeUpper(request.registrationNumber());

        truckRepository.findByRegistrationNumber(normalizedRegistrationNumber).ifPresent(truck -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "registration number already used");
        });

        Truck truck = Truck.builder()
            .registrationNumber(normalizedRegistrationNumber)
            .brand(request.brand().trim())
            .model(request.model().trim())
            .capacity(request.capacity())
            .status(TruckStatus.AVAILABLE)
            .build();

        return fleetMapper.toTruckResponse(truckRepository.save(truck));
    }

    @Transactional(readOnly = true)
    public List<TruckResponse> getAllTrucks() {
        return truckRepository.findAll()
            .stream()
            .map(fleetMapper::toTruckResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public TruckResponse getTruckById(UUID truckId) {
        Truck truck = truckRepository.findById(truckId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "truck not found"));
        return fleetMapper.toTruckResponse(truck);
    }

    @Transactional(readOnly = true)
    public List<TruckResponse> getAvailableTrucks() {
        return truckRepository.findByStatus(TruckStatus.AVAILABLE)
            .stream()
            .map(fleetMapper::toTruckResponse)
            .toList();
    }

    @Transactional
    public void deleteTruck(UUID truckId) {
        Truck truck = truckRepository.findById(truckId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "truck not found"));
        if (truck.getStatus() == TruckStatus.ASSIGNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cannot delete a truck that is currently assigned to a driver");
        }
        truckRepository.delete(truck);
    }

    @Transactional
    public TruckResponse assignDriverToTruck(UUID truckId, AssignTruckRequest request) {
        Truck truck = truckRepository.findById(truckId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "truck not found"));

        Driver driver = driverRepository.findById(request.driverId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "driver not found"));

        if (!driver.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "driver is inactive");
        }

        if (truck.getStatus() != TruckStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "truck is not available");
        }

        if (truckRepository.existsByAssignedDriverId(driver.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "driver already assigned to another truck");
        }

        truck.setAssignedDriver(driver);
        truck.setStatus(TruckStatus.ASSIGNED);
        return fleetMapper.toTruckResponse(truckRepository.save(truck));
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> getAllDrivers() {
        return driverRepository.findAll()
            .stream()
            .map(fleetMapper::toDriverResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public DriverResponse getDriverById(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "driver not found"));
        return fleetMapper.toDriverResponse(driver);
    }

    @Transactional
    public void deleteDriver(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "driver not found"));
        if (truckRepository.existsByAssignedDriverId(driverId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cannot delete a driver currently assigned to a truck");
        }
        driverRepository.delete(driver);
    }

    private String normalizeUpper(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePhoneNumber(String value) {
        return value.trim();
    }

    private boolean isDriverProfileComplete(Driver driver) {
        return driver.getLicenseNumber() != null
            && !driver.getLicenseNumber().isBlank()
            && driver.getPhoneNumber() != null
            && !driver.getPhoneNumber().isBlank();
    }
}

package com.fleet.fleet.repository;

import com.fleet.fleet.entity.Truck;
import com.fleet.fleet.entity.TruckStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TruckRepository extends JpaRepository<Truck, UUID> {
    List<Truck> findByStatus(TruckStatus status);
    Optional<Truck> findByRegistrationNumber(String registrationNumber);
    boolean existsByAssignedDriverId(UUID driverId);
}

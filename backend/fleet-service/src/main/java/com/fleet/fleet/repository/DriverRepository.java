package com.fleet.fleet.repository;

import com.fleet.fleet.entity.Driver;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, UUID> {
    Optional<Driver> findByAuthUserId(UUID authUserId);
    boolean existsByMatricule(String matricule);
}

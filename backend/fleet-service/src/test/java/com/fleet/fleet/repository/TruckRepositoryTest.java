package com.fleet.fleet.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fleet.fleet.entity.Driver;
import com.fleet.fleet.entity.Truck;
import com.fleet.fleet.entity.TruckStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class TruckRepositoryTest {

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private TruckRepository truckRepository;

    @Test
    void shouldFindOnlyAvailableTrucks() {
        Driver driver = driverRepository.save(Driver.builder()
            .authUserId(UUID.randomUUID())
            .fullName("Driver One")
            .matricule("DRV-001")
            .licenseNumber("LIC-001")
            .phoneNumber("+21611111111")
            .active(true)
            .build());

        truckRepository.save(Truck.builder()
            .registrationNumber("TRK-001")
            .brand("Volvo")
            .model("FH")
            .capacity(18.0)
            .status(TruckStatus.AVAILABLE)
            .build());

        truckRepository.save(Truck.builder()
            .registrationNumber("TRK-002")
            .brand("Scania")
            .model("R500")
            .capacity(20.0)
            .status(TruckStatus.ASSIGNED)
            .assignedDriver(driver)
            .build());

        List<Truck> availableTrucks = truckRepository.findByStatus(TruckStatus.AVAILABLE);

        assertThat(availableTrucks).hasSize(1);
        assertThat(availableTrucks.get(0).getRegistrationNumber()).isEqualTo("TRK-001");
    }

    @Test
    void shouldDetectAssignedDriver() {
        Driver driver = driverRepository.save(Driver.builder()
            .authUserId(UUID.randomUUID())
            .fullName("Driver Two")
            .matricule("DRV-002")
            .licenseNumber("LIC-002")
            .phoneNumber("+21622222222")
            .active(true)
            .build());

        truckRepository.save(Truck.builder()
            .registrationNumber("TRK-003")
            .brand("Mercedes")
            .model("Actros")
            .capacity(22.0)
            .status(TruckStatus.ASSIGNED)
            .assignedDriver(driver)
            .build());

        assertThat(truckRepository.existsByAssignedDriverId(driver.getId())).isTrue();
    }
}

package com.fleet.fleet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fleet.fleet.dto.AssignTruckRequest;
import com.fleet.fleet.dto.CreateDriverRequest;
import com.fleet.fleet.dto.CreateTruckRequest;
import com.fleet.fleet.dto.UpdateDriverActivationRequest;
import com.fleet.fleet.dto.UpdateMyDriverProfileRequest;
import com.fleet.fleet.entity.Driver;
import com.fleet.fleet.entity.Truck;
import com.fleet.fleet.entity.TruckStatus;
import com.fleet.fleet.mapper.FleetMapper;
import com.fleet.fleet.repository.DriverRepository;
import com.fleet.fleet.repository.TruckRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FleetServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private TruckRepository truckRepository;

    private FleetMapper fleetMapper;

    private FleetService fleetService;

    private Driver driver;
    private Truck truck;

    @BeforeEach
    void setUp() {
        fleetMapper = new FleetMapper();
        fleetService = new FleetService(driverRepository, truckRepository, fleetMapper);

        driver = Driver.builder()
            .id(UUID.randomUUID())
            .authUserId(UUID.randomUUID())
            .fullName("Ahmed Ben Ali")
            .matricule("DRV-100")
            .licenseNumber("LIC-100")
            .phoneNumber("+21611111111")
            .active(true)
            .build();

        truck = Truck.builder()
            .id(UUID.randomUUID())
            .registrationNumber("TRK-100")
            .brand("Volvo")
            .model("FH")
            .capacity(15.0)
            .status(TruckStatus.AVAILABLE)
            .build();
    }

    @Test
    void shouldCreateDriver() {
        CreateDriverRequest request = new CreateDriverRequest(
            driver.getAuthUserId(),
            "Ahmed Ben Ali",
            "drv-100"
        );

        when(driverRepository.findByAuthUserId(driver.getAuthUserId())).thenReturn(Optional.empty());
        when(driverRepository.existsByMatricule("DRV-100")).thenReturn(false);
        when(driverRepository.save(any(Driver.class))).thenAnswer(invocation -> {
            Driver saved = invocation.getArgument(0);
            saved.setId(driver.getId());
            return saved;
        });

        var response = fleetService.createDriver(request);

        assertThat(response.id()).isEqualTo(driver.getId());
        assertThat(response.authUserId()).isEqualTo(driver.getAuthUserId());
        assertThat(response.fullName()).isEqualTo("Ahmed Ben Ali");
        assertThat(response.matricule()).isEqualTo("DRV-100");
        assertThat(response.licenseNumber()).isNull();
        assertThat(response.phoneNumber()).isNull();
        assertThat(response.active()).isFalse();
    }

    @Test
    void shouldRejectCreateDriverWhenAuthUserAlreadyLinked() {
        CreateDriverRequest request = new CreateDriverRequest(
            driver.getAuthUserId(),
            "Ahmed Ben Ali",
            "drv-100"
        );

        when(driverRepository.findByAuthUserId(driver.getAuthUserId())).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> fleetService.createDriver(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("auth user already linked to a driver");
    }

    @Test
    void shouldRejectCreateDriverWhenMatriculeAlreadyUsed() {
        CreateDriverRequest request = new CreateDriverRequest(
            driver.getAuthUserId(),
            "Ahmed Ben Ali",
            "drv-100"
        );

        when(driverRepository.findByAuthUserId(driver.getAuthUserId())).thenReturn(Optional.empty());
        when(driverRepository.existsByMatricule("DRV-100")).thenReturn(true);

        assertThatThrownBy(() -> fleetService.createDriver(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("matricule already used");
    }

    @Test
    void shouldUpdateMyDriverProfile() {
        when(driverRepository.findByAuthUserId(driver.getAuthUserId())).thenReturn(Optional.of(driver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = fleetService.updateMyDriverProfile(
            driver.getAuthUserId(),
            new UpdateMyDriverProfileRequest("lic-900", "+216 22 333 444")
        );

        assertThat(response.licenseNumber()).isEqualTo("LIC-900");
        assertThat(response.phoneNumber()).isEqualTo("+216 22 333 444");
        assertThat(response.fullName()).isEqualTo("Ahmed Ben Ali");
        assertThat(response.matricule()).isEqualTo("DRV-100");
    }

    @Test
    void shouldThrowNotFoundWhenDriverProfileMissingOnUpdate() {
        UUID randomUserId = UUID.randomUUID();
        when(driverRepository.findByAuthUserId(randomUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fleetService.updateMyDriverProfile(
            randomUserId,
            new UpdateMyDriverProfileRequest("lic-900", "+216 22 333 444")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("driver not found");
    }

    @Test
    void shouldActivateDriverWhenProfileComplete() {
        when(driverRepository.findById(driver.getId())).thenReturn(Optional.of(driver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = fleetService.updateDriverActivation(
            driver.getId(),
            new UpdateDriverActivationRequest(true)
        );

        assertThat(response.active()).isTrue();
    }

    @Test
    void shouldRejectActivationWhenProfileIncomplete() {
        driver.setPhoneNumber(null);
        when(driverRepository.findById(driver.getId())).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> fleetService.updateDriverActivation(
            driver.getId(),
            new UpdateDriverActivationRequest(true)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("driver profile is incomplete");
    }

    @Test
    void shouldReturnAvailableTrucks() {
        when(truckRepository.findByStatus(TruckStatus.AVAILABLE)).thenReturn(List.of(truck));

        var result = fleetService.getAvailableTrucks();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).registrationNumber()).isEqualTo("TRK-100");
    }

    @Test
    void shouldAssignDriverToTruck() {
        when(truckRepository.findById(truck.getId())).thenReturn(Optional.of(truck));
        when(driverRepository.findById(driver.getId())).thenReturn(Optional.of(driver));
        when(truckRepository.existsByAssignedDriverId(driver.getId())).thenReturn(false);
        when(truckRepository.save(any(Truck.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = fleetService.assignDriverToTruck(truck.getId(), new AssignTruckRequest(driver.getId()));

        assertThat(response.status()).isEqualTo(TruckStatus.ASSIGNED);
        assertThat(response.assignedDriver()).isNotNull();
        assertThat(response.assignedDriver().id()).isEqualTo(driver.getId());
        assertThat(response.assignedDriver().matricule()).isEqualTo("DRV-100");
        assertThat(response.assignedDriver().fullName()).isEqualTo("Ahmed Ben Ali");
        verify(truckRepository).save(truck);
    }

    @Test
    void shouldRejectAssignWhenTruckNotAvailable() {
        truck.setStatus(TruckStatus.MAINTENANCE);
        when(truckRepository.findById(truck.getId())).thenReturn(Optional.of(truck));
        when(driverRepository.findById(driver.getId())).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> fleetService.assignDriverToTruck(truck.getId(), new AssignTruckRequest(driver.getId())))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("truck is not available");
    }

    @Test
    void shouldCreateTruck() {
        CreateTruckRequest request = new CreateTruckRequest("trk-200", "MAN", "TGX", 25.0);
        when(truckRepository.findByRegistrationNumber("TRK-200")).thenReturn(Optional.empty());
        when(truckRepository.save(any(Truck.class))).thenAnswer(invocation -> {
            Truck saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var response = fleetService.createTruck(request);

        assertThat(response.registrationNumber()).isEqualTo("TRK-200");
        assertThat(response.status()).isEqualTo(TruckStatus.AVAILABLE);
    }
}

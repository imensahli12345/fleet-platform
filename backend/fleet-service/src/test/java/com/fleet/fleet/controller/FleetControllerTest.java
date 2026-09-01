package com.fleet.fleet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleet.fleet.dto.AssignTruckRequest;
import com.fleet.fleet.dto.CreateDriverRequest;
import com.fleet.fleet.dto.CreateTruckRequest;
import com.fleet.fleet.dto.DriverResponse;
import com.fleet.fleet.dto.TruckResponse;
import com.fleet.fleet.dto.UpdateDriverActivationRequest;
import com.fleet.fleet.dto.UpdateMyDriverProfileRequest;
import com.fleet.fleet.entity.TruckStatus;
import com.fleet.fleet.service.FleetService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FleetController.class)
@AutoConfigureMockMvc(addFilters = false)
class FleetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FleetService fleetService;

    @Test
    void shouldCreateDriver() throws Exception {
        UUID authUserId = UUID.randomUUID();
        DriverResponse response = new DriverResponse(
            UUID.randomUUID(),
            authUserId,
            "Ahmed Ben Ali",
            "DRV-100",
            null,
            null,
            false
        );

        when(fleetService.createDriver(any(CreateDriverRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/fleet/drivers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateDriverRequest(authUserId, "Ahmed Ben Ali", "DRV-100"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.authUserId").value(authUserId.toString()))
            .andExpect(jsonPath("$.fullName").value("Ahmed Ben Ali"))
            .andExpect(jsonPath("$.matricule").value("DRV-100"))
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldUpdateMyDriverProfile() throws Exception {
        UUID authUserId = UUID.randomUUID();
        DriverResponse response = new DriverResponse(
            UUID.randomUUID(),
            authUserId,
            "Ahmed Ben Ali",
            "DRV-100",
            "LIC-777",
            "+21600000000",
            false
        );

        when(fleetService.updateMyDriverProfile(eq(authUserId), any(UpdateMyDriverProfileRequest.class)))
            .thenReturn(response);

        mockMvc.perform(patch("/api/fleet/drivers/me")
                .principal(() -> authUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateMyDriverProfileRequest(
                    "LIC-777",
                    "+21600000000"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName").value("Ahmed Ben Ali"))
            .andExpect(jsonPath("$.matricule").value("DRV-100"))
            .andExpect(jsonPath("$.licenseNumber").value("LIC-777"))
            .andExpect(jsonPath("$.phoneNumber").value("+21600000000"));
    }

    @Test
    void shouldUpdateDriverActivation() throws Exception {
        UUID driverId = UUID.randomUUID();
        DriverResponse response = new DriverResponse(
            driverId,
            UUID.randomUUID(),
            "Ahmed Ben Ali",
            "DRV-100",
            "LIC-777",
            "+21600000000",
            true
        );

        when(fleetService.updateDriverActivation(eq(driverId), any(UpdateDriverActivationRequest.class)))
            .thenReturn(response);

        mockMvc.perform(patch("/api/fleet/drivers/{driverId}/activation", driverId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateDriverActivationRequest(true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldReturnAvailableTrucks() throws Exception {
        TruckResponse response = new TruckResponse(
            UUID.randomUUID(),
            "TRK-001",
            "Volvo",
            "FH",
            18.0,
            TruckStatus.AVAILABLE,
            null
        );

        when(fleetService.getAvailableTrucks()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/fleet/trucks/available"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].registrationNumber").value("TRK-001"))
            .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    void shouldAssignDriverToTruck() throws Exception {
        UUID truckId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        TruckResponse.AssignedDriverResponse assignedDriver = new TruckResponse.AssignedDriverResponse(
            driverId,
            "DRV-010",
            "Driver Assigned"
        );
        TruckResponse response = new TruckResponse(
            truckId,
            "TRK-010",
            "Scania",
            "R450",
            20.0,
            TruckStatus.ASSIGNED,
            assignedDriver
        );

        when(fleetService.assignDriverToTruck(eq(truckId), any(AssignTruckRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/fleet/trucks/{truckId}/assign", truckId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AssignTruckRequest(driverId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ASSIGNED"))
            .andExpect(jsonPath("$.assignedDriver.id").value(driverId.toString()))
            .andExpect(jsonPath("$.assignedDriver.matricule").value("DRV-010"))
            .andExpect(jsonPath("$.assignedDriver.fullName").value("Driver Assigned"));
    }

    @Test
    void shouldCreateTruck() throws Exception {
        TruckResponse response = new TruckResponse(
            UUID.randomUUID(),
            "TRK-200",
            "MAN",
            "TGX",
            25.0,
            TruckStatus.AVAILABLE,
            null
        );

        when(fleetService.createTruck(any(CreateTruckRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/fleet/trucks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateTruckRequest(
                    "TRK-200",
                    "MAN",
                    "TGX",
                    25.0
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.registrationNumber").value("TRK-200"))
            .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }
}

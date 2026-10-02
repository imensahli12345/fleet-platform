package com.fleet.shipment.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void httpStatusMatchesTheExceptionStatus() {
        for (HttpStatus status : new HttpStatus[] {
                HttpStatus.BAD_REQUEST, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND,
                HttpStatus.CONFLICT, HttpStatus.SERVICE_UNAVAILABLE}) {
            ResponseEntity<Map<String, Object>> response =
                    handler.handleResponseStatus(new ResponseStatusException(status, "reason"));

            assertThat(response.getStatusCode()).isEqualTo(status);
            assertThat(response.getBody())
                    .containsEntry("status", status.value())
                    .containsEntry("error", "reason");
        }
    }
}

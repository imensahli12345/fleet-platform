package com.fleet.shipment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Exercises the real @CircuitBreaker proxy on FleetGateway with the same breaker settings as
 * config-repo/shipment-service.yml, against a mocked fleet-service client:
 *  - outages (unreachable / 5xx) trip the breaker to OPEN, then calls fail instantly
 *  - 404 "not found" answers never trip it
 *  - after the open wait, successful trial calls close it again
 */
@SpringBootTest(classes = FleetGateway.class, properties = {
        "resilience4j.circuitbreaker.instances.fleet-service.sliding-window-type=COUNT_BASED",
        "resilience4j.circuitbreaker.instances.fleet-service.sliding-window-size=10",
        "resilience4j.circuitbreaker.instances.fleet-service.minimum-number-of-calls=5",
        "resilience4j.circuitbreaker.instances.fleet-service.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.fleet-service.wait-duration-in-open-state=10s",
        "resilience4j.circuitbreaker.instances.fleet-service.permitted-number-of-calls-in-half-open-state=3",
        "resilience4j.circuitbreaker.instances.fleet-service.record-exceptions[0]=feign.RetryableException",
        "resilience4j.circuitbreaker.instances.fleet-service.record-exceptions[1]=feign.FeignException$FeignServerException"
})
@ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class})
class FleetGatewayCircuitBreakerTest {

    private static final Request REQUEST = Request.create(Request.HttpMethod.GET,
            "http://fleet-service/api/fleet/trucks/available", Collections.emptyMap(), null,
            StandardCharsets.UTF_8, null);

    @Autowired
    private FleetGateway gateway;

    @Autowired
    private CircuitBreakerRegistry registry;

    @MockBean
    private FleetServiceClient client;

    private CircuitBreaker breaker;

    @BeforeEach
    void freshBreaker() {
        breaker = registry.circuitBreaker(FleetGateway.BREAKER);
        breaker.reset();
        reset(client);
    }

    @Test
    void unreachableFleetServiceOpensTheBreakerThenCallsFailFast() {
        when(client.getAvailableTrucks()).thenThrow(unreachable());

        for (int i = 0; i < 5; i++) {
            assertThrows(RetryableException.class, () -> gateway.getAvailableTrucks());
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // OPEN: rejected instantly, fleet-service is not even called.
        assertThrows(CallNotPermittedException.class, () -> gateway.getAvailableTrucks());
        verify(client, times(5)).getAvailableTrucks();
    }

    @Test
    void serverErrorsOpenTheBreaker() {
        when(client.getTruckById(any())).thenThrow(httpError(503));

        for (int i = 0; i < 5; i++) {
            assertThrows(FeignException.ServiceUnavailable.class, () -> gateway.getTruckById(UUID.randomUUID()));
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void notFoundAnswersNeverOpenTheBreaker() {
        when(client.getTruckById(any())).thenThrow(httpError(404));

        for (int i = 0; i < 10; i++) {
            assertThrows(FeignException.NotFound.class, () -> gateway.getTruckById(UUID.randomUUID()));
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void successfulTrialCallsCloseTheBreakerAgain() {
        breaker.transitionToOpenState();
        assertThrows(CallNotPermittedException.class, () -> gateway.getAvailableTrucks());
        verify(client, never()).getAvailableTrucks();

        // What the 10s open wait leads to: HALF_OPEN, where 3 trial calls are allowed.
        breaker.transitionToHalfOpenState();
        when(client.getAvailableTrucks()).thenReturn(List.of());
        for (int i = 0; i < 3; i++) {
            gateway.getAvailableTrucks();
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private static RetryableException unreachable() {
        return new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null, REQUEST);
    }

    private static FeignException httpError(int status) {
        Response response = Response.builder()
                .status(status)
                .reason("test")
                .request(REQUEST)
                .headers(Collections.emptyMap())
                .build();
        return FeignException.errorStatus("FleetServiceClient#call", response);
    }
}

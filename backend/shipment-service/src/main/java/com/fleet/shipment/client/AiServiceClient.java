package com.fleet.shipment.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fleet.shipment.exception.AiAnalysisFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.util.UUID;

/**
 * Client for the external ai-service (Dispatch Exception CoPilot).
 * Plain RestClient — NOT Feign/Eureka, because ai-service is external
 * to the Spring Cloud service mesh, authenticated via a static API key.
 */
@Component
public class AiServiceClient {

    private final RestClient restClient;

    public AiServiceClient(
            @Value("${ai.service.url}") String aiServiceUrl,
            @Value("${ai.service.apiKey}") String apiKey,
            @Value("${ai.service.timeoutMs:14000}") int timeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(aiServiceUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(requestFactory)
                .build();
    }

    public AnalyzeResponse analyze(String text, UUID shipmentId) {
        AnalyzeRequest request = new AnalyzeRequest(
                text,
                shipmentId.toString(),
                UUID.randomUUID().toString()
        );

        try {
            AnalyzeResponse response = restClient.post()
                    .uri("/v1/exceptions:analyze")
                    .body(request)
                    .retrieve()
                    .body(AnalyzeResponse.class);

            if (response == null) {
                throw new AiAnalysisFailedException("ai-service returned an empty response");
            }
            return response;

        } catch (HttpStatusCodeException e) {
            // Any non-2xx status — treated as failure, never coerced
            throw new AiAnalysisFailedException(
                    "ai-service returned " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);

        } catch (ResourceAccessException e) {
            // Timeout or network failure
            throw new AiAnalysisFailedException("ai-service timed out or unreachable", e);

        } catch (IllegalArgumentException e) {
            // Enum values out of taxonomy (e.g. Severity.valueOf on bad string) surface here
            // if you deserialize severity/category as enums directly in AnalyzeResponse.
            throw new AiAnalysisFailedException("ai-service returned an unrecognized value: " + e.getMessage(), e);

        } catch (Exception e) {
            // Catch-all: covers HttpMessageConversionException (JSON field name mismatch,
            // e.g. ai-service returns snake_case but records expect camelCase), and any
            // other RestClientException subclass not matched above.
            throw new AiAnalysisFailedException("Unexpected error calling ai-service: " + e.getMessage(), e);
        }
    }

    // ---- DTOs matching the exact contract ----

    public record AnalyzeRequest(
            String text,
            String shipmentId,
            String requestId
    ) {}

    // AnalyzeResponse: accepts both snake_case and camelCase. ai-service (Python) actually emits
    // camelCase field names directly (see app/models.py) -- for the fields added below, camelCase
    // is the primary @JsonProperty name and snake_case is only a defensive @JsonAlias.
    public record AnalyzeResponse(
            @JsonProperty("structured_record") @JsonAlias("structuredRecord") StructuredRecord structuredRecord,
            @JsonProperty("action_plan")       @JsonAlias("actionPlan")       String actionPlan,
            @JsonProperty("customer_notification") @JsonAlias("customerNotification") String customerNotification,
            @JsonProperty("analysisSource") @JsonAlias("analysis_source") String analysisSource
    ) {}

    public record StructuredRecord(
            String severity,     // kept as String here, parsed to enum in ShipmentService
            String category,     // same — lets us catch bad taxonomy explicitly, not via Jackson
            @JsonProperty("eta_impact") @JsonAlias("etaImpact") String etaImpact,
            @JsonProperty("needsReview") @JsonAlias("needs_review") Boolean needsReview,
            @JsonProperty("confidence") Double confidence
    ) {}
}
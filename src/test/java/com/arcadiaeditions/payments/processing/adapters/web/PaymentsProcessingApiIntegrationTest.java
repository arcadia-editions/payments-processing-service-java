package com.arcadiaeditions.payments.processing.adapters.web;

import static org.springframework.http.HttpMethod.*;

import com.arcadiaeditions.payments.processing.adapters.web.dtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Integration tests for the {@link PaymentsProcessingApi} REST controller.
 */
public class PaymentsProcessingApiIntegrationTest extends BaseWebTestClientTest {

    /**
     * Test: Read only, for the reservation timer. STARTED for any existing Payment. for OK.
     */
    @Test
    public void testGetPaymentStatus_201() {
        String requestBody = """
            {
              "orderId" : "ORD-001"
            }
        """;

        webTestClient
                .method(POST)
                .uri("/api/payments/status")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus()
                .isEqualTo(201)
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.outcome")
                .isEqualTo("STARTED")
                .jsonPath("$.started.orderId")
                .isEqualTo("ORD-001")
                .jsonPath("$.started.status")
                .isEqualTo("CAPTURED")
                .jsonPath("$.noPayment")
                .doesNotExist();
    }

    /**
     * Test: Read only, for the reservation timer. STARTED for any existing Payment. for Invalid request.
     */
    @Test
    public void testGetPaymentStatus_400() {
        // orderId is required (@NotNull on GetPaymentStatusInputDTO). A null value is an invalid request.
        String requestBody = """
            {
              "orderId" : null
            }
        """;

        webTestClient
                .method(POST)
                .uri("/api/payments/status")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus()
                .isEqualTo(400);
    }
}

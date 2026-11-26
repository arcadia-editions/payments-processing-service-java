package com.arcadiaeditions.payments.processing.adapters.web;

import static org.springframework.http.HttpMethod.POST;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Flow test of the reservation timer read (place-order-flow ZFL 268-297), payments side:
 * catalog-inventory's timer calls POST /api/payments/status.
 */
public class PlaceOrderFlowReservationTimerIntegrationTest extends BaseWebTestClientTest {

    private WebTestClient.BodyContentSpec status(String orderId) {
        return webTestClient
                .method(POST)
                .uri("/api/payments/status")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\":\"" + orderId + "\"}")
                .exchange()
                .expectStatus()
                .isEqualTo(201)
                .expectBody();
    }

    /** C-11: no Payment row, the checkout stalled before payment: timer releases the stock. */
    @Test
    public void noPaymentForOrderWithoutPayment() {
        status("ORD-011")
                .jsonPath("$.outcome")
                .isEqualTo("NO_PAYMENT")
                .jsonPath("$.noPayment.orderId")
                .isEqualTo("ORD-011")
                .jsonPath("$.started")
                .doesNotExist();
    }

    /** C-15: first authorization call in flight: payment owns the end, timer does nothing. */
    @Test
    public void startedWhenAuthorizationInFlight() {
        status("ORD-015")
                .jsonPath("$.outcome")
                .isEqualTo("STARTED")
                .jsonPath("$.started.orderId")
                .isEqualTo("ORD-015")
                .jsonPath("$.started.status")
                .isEqualTo("AUTHORIZATION_REQUESTED")
                .jsonPath("$.noPayment")
                .doesNotExist();
    }
}

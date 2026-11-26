package com.arcadiaeditions.payments.processing.adapters.web;

import com.arcadiaeditions.payments.processing.adapters.web.dtos.*;
import com.arcadiaeditions.payments.processing.config.ServicesInMemoryConfig;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test controller for PaymentsProcessingApiController.
 */
public class PaymentsProcessingApiControllerTest {

    private final Logger log = LoggerFactory.getLogger(getClass());

    ServicesInMemoryConfig context = new ServicesInMemoryConfig();

    PaymentsProcessingApiController controller =
            new PaymentsProcessingApiController(context.paymentsProcessingService());

    @BeforeEach
    void setUp() {
        context.reloadTestData();
    }

    @Test
    public void getPaymentStatusTest() {
        var response = controller.getPaymentStatus(new GetPaymentStatusInputDTO().orderId("ORD-001"));
        Assertions.assertEquals(201, response.getStatusCode().value());
        Assertions.assertEquals(
                GetPaymentStatusOutcomeDTO.STARTED, response.getBody().getOutcome());
        Assertions.assertEquals("ORD-001", response.getBody().getStarted().getOrderId());
        Assertions.assertNull(response.getBody().getNoPayment());

        var none = controller.getPaymentStatus(new GetPaymentStatusInputDTO().orderId("ORD-011"));
        Assertions.assertEquals(
                GetPaymentStatusOutcomeDTO.NO_PAYMENT, none.getBody().getOutcome());
        Assertions.assertEquals("ORD-011", none.getBody().getNoPayment().getOrderId());
    }
}

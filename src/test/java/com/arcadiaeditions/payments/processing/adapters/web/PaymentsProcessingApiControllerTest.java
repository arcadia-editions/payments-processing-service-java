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
        GetPaymentStatusInputDTO reqBody = null;
        var response = controller.getPaymentStatus(reqBody);
        Assertions.assertEquals(201, response.getStatusCode().value());
    }
}

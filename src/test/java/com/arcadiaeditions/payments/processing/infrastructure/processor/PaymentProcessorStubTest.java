package com.arcadiaeditions.payments.processing.infrastructure.processor;

import static com.arcadiaeditions.payments.processing.core.outbound.processor.PaymentProcessor.*;
import static org.junit.jupiter.api.Assertions.*;

import com.arcadiaeditions.payments.processing.core.outbound.processor.PaymentProcessor;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PaymentProcessorStubTest {

    static final String ORDER_ID = "ord_1";
    static final BigDecimal AMOUNT = new BigDecimal("42.50");

    PaymentProcessor processor = new StandInPaymentProcessor(Duration.ofMillis(200));

    AuthorizationResult authorize(String paymentMethodId, int attemptCount) {
        return processor.authorize(new AuthorizationRequest(ORDER_ID, paymentMethodId, AMOUNT, "EUR", attemptCount));
    }

    @Test
    void authorizeAuthorized() {
        var result = authorize("pm_card_visa", 1);
        assertEquals(AuthorizationOutcome.AUTHORIZED, result.outcome());
        assertEquals("STANDIN-" + ORDER_ID, result.providerReference());
        assertNull(result.reason());
    }

    @Test
    void authorizeCaptureFailReference() {
        var result = authorize(StandInPaymentProcessor.PM_CAPTURE_FAIL, 1);
        assertEquals(AuthorizationOutcome.AUTHORIZED, result.outcome());
        assertEquals("STANDIN-CAPFAIL-" + ORDER_ID, result.providerReference());
    }

    @Test
    void authorizeDeclined() {
        var result = authorize(StandInPaymentProcessor.PM_DECLINE, 1);
        assertEquals(AuthorizationOutcome.DECLINED, result.outcome());
        assertEquals(CARD_DECLINED, result.reason());
        assertNull(result.providerReference());
    }

    @Test
    void authorizeFailOnce() {
        var first = authorize(StandInPaymentProcessor.PM_FAIL_ONCE, 1);
        assertEquals(AuthorizationOutcome.FAILED, first.outcome());
        assertEquals(PROCESSOR_UNAVAILABLE, first.reason());
        var second = authorize(StandInPaymentProcessor.PM_FAIL_ONCE, 2);
        assertEquals(AuthorizationOutcome.AUTHORIZED, second.outcome());
        assertEquals("STANDIN-" + ORDER_ID, second.providerReference());
    }

    @Test
    void authorizeFailAlways() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            var result = authorize(StandInPaymentProcessor.PM_FAIL_ALWAYS, attempt);
            assertEquals(AuthorizationOutcome.FAILED, result.outcome());
            assertEquals(PROCESSOR_UNAVAILABLE, result.reason());
        }
    }

    @Test
    void authorizeTimeout() {
        long start = System.nanoTime();
        var result = authorize(StandInPaymentProcessor.PM_TIMEOUT, 1);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertEquals(AuthorizationOutcome.FAILED, result.outcome());
        assertEquals(PROCESSOR_TIMEOUT, result.reason());
        assertTrue(elapsedMs < 5000, "timed out after the configured timeout, not the processor delay: " + elapsedMs);
    }

    @Test
    void defaultTimeoutIs30Seconds() {
        assertEquals(Duration.ofSeconds(30), StandInPaymentProcessor.DEFAULT_TIMEOUT);
    }

    @Test
    void captureCaptured() {
        var result =
                processor.capture(new CaptureRequest(ORDER_ID, "pm_card_visa", "STANDIN-" + ORDER_ID, AMOUNT, "EUR"));
        assertTrue(result.captured());
        assertNull(result.reason());
    }

    @Test
    void captureFailed() {
        var result = processor.capture(new CaptureRequest(
                ORDER_ID, StandInPaymentProcessor.PM_CAPTURE_FAIL, "STANDIN-CAPFAIL-" + ORDER_ID, AMOUNT, "EUR"));
        assertFalse(result.captured());
        assertEquals(CAPTURE_REJECTED, result.reason());
    }

    @Test
    void voidVoided() {
        var result = processor.voidAuthorization(
                new VoidRequest(ORDER_ID, "pm_card_visa", "STANDIN-" + ORDER_ID, "order_cancelled"));
        assertTrue(result.voided());
        assertNull(result.reason());
    }

    @Test
    void voidFailed() {
        var result = processor.voidAuthorization(new VoidRequest(
                ORDER_ID, StandInPaymentProcessor.PM_VOID_FAIL, "STANDIN-" + ORDER_ID, "order_cancelled"));
        assertFalse(result.voided());
        assertEquals(VOID_REJECTED, result.reason());
    }
}

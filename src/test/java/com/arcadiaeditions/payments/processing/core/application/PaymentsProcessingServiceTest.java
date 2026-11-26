package com.arcadiaeditions.payments.processing.core.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.arcadiaeditions.payments.processing.config.*;
import com.arcadiaeditions.payments.processing.core.application.mappers.*;
import com.arcadiaeditions.payments.processing.core.domain.*;
import com.arcadiaeditions.payments.processing.core.inbound.*;
import com.arcadiaeditions.payments.processing.core.inbound.dtos.*;
import com.arcadiaeditions.payments.processing.core.outbound.jpa.*;
import com.arcadiaeditions.payments.processing.core.outbound.processor.PaymentProcessor;
import com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized;
import com.arcadiaeditions.payments.processing.events.avro.PaymentCaptured;
import com.arcadiaeditions.payments.processing.infrastructure.jpa.inmemory.*;
import com.arcadiaeditions.payments.processing.infrastructure.processor.StandInPaymentProcessor;
import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Acceptance Test for PaymentsProcessingService.
 */
class PaymentsProcessingServiceTest {

    private final Logger log = LoggerFactory.getLogger(getClass());

    ServicesInMemoryConfig context = new ServicesInMemoryConfig();
    PaymentsProcessingServiceImpl paymentsProcessingService = context.paymentsProcessingService();

    PaymentRepositoryInMemory paymentRepository = context.paymentRepository();

    @BeforeEach
    void setUp() {
        context.reloadTestData();
    }

    AuthorizePaymentInput newAuthorizePaymentInput(String orderId) {
        return new AuthorizePaymentInput()
                .setOrderId(orderId)
                .setCustomerId("CUST-099")
                .setCustomerEmail("customer099@example.com")
                .setAmount(new BigDecimal("29.99"))
                .setCurrency("EUR")
                .setPaymentMethodId("PM-099");
    }

    @Test
    void authorizePaymentTest() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var payment = paymentsProcessingService.authorizePayment(newAuthorizePaymentInput("ORD-099"));

        var saved = paymentRepository.findByOrderId("ORD-099").orElseThrow();
        assertNotNull(saved.getId());
        assertEquals(PaymentStatus.AUTHORIZED, saved.getStatus());
        assertEquals(1, saved.getAttemptCount());
        assertEquals("STANDIN-ORD-099", saved.getProviderReference());
        assertEquals(TestClock.NOW, saved.getAuthorizedAt());
        assertEquals("CUST-099", saved.getCustomerId());
        assertEquals("PM-099", saved.getPaymentMethodId());
        assertEquals("customer099@example.com", saved.getCustomerEmail());
        assertEquals(0, new BigDecimal("29.99").compareTo(saved.getAmount()));
        assertEquals("EUR", saved.getCurrency());
        assertNull(saved.getDeclinedAt());
        assertNull(saved.getFailedAt());
        assertNull(saved.getFailureReason());
        assertEquals(saved.getId(), payment.getId());

        assertEquals(1, producer.getOnPaymentAuthorizedCapturedMessages().size());
        assertEquals(0, producer.getOnPaymentDeclinedCapturedMessages().size());
        assertEquals(0, producer.getOnPaymentFailedCapturedMessages().size());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
        var event = (PaymentAuthorized)
                producer.getOnPaymentAuthorizedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-099", event.getOrderId().toString());
        assertEquals("CUST-099", event.getCustomerId().toString());
        assertEquals("STANDIN-ORD-099", event.getProviderReference().toString());
        assertEquals(TestClock.NOW.toString(), event.getAuthorizedAt().toString());
    }

    @Test
    void authorizePaymentSavesAuthorizationRequestedBeforeCallingProcessor() {
        var seen = new ArrayList<Payment>();
        var standIn = new StandInPaymentProcessor(Duration.ofMillis(500));
        PaymentProcessor recording = new PaymentProcessor() {
            public AuthorizationResult authorize(AuthorizationRequest request) {
                var p = paymentRepository.findByOrderId(request.orderId()).orElseThrow();
                seen.add(new Payment().setStatus(p.getStatus()).setAttemptCount(p.getAttemptCount()));
                assertEquals(1, request.attemptCount());
                return standIn.authorize(request);
            }

            public CaptureResult capture(CaptureRequest request) {
                return standIn.capture(request);
            }

            public VoidResult voidAuthorization(VoidRequest request) {
                return standIn.voidAuthorization(request);
            }
        };
        var service = new PaymentsProcessingServiceImpl(
                paymentRepository,
                context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer(),
                TestClock.FIXED,
                recording);

        service.authorizePayment(newAuthorizePaymentInput("ORD-099"));

        assertEquals(1, seen.size());
        assertEquals(PaymentStatus.AUTHORIZATION_REQUESTED, seen.get(0).getStatus());
        assertEquals(1, seen.get(0).getAttemptCount());
        assertEquals(
                PaymentStatus.AUTHORIZED,
                paymentRepository.findByOrderId("ORD-099").orElseThrow().getStatus());
    }

    @Test
    void authorizePaymentRedeliveryIsNoOp() { // C-02: a Payment for ORD-002 exists (AUTHORIZED)
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var count = paymentRepository.count();
        var input = newAuthorizePaymentInput("ORD-002").setPaymentMethodId("PM-002");

        var payment = paymentsProcessingService.authorizePayment(input);

        assertEquals(count, paymentRepository.count());
        var existing = paymentRepository.findByOrderId("ORD-002").orElseThrow();
        assertEquals(existing.getId(), payment.getId());
        assertEquals(PaymentStatus.AUTHORIZED, existing.getStatus());
        assertEquals(1, existing.getAttemptCount());
        assertEquals("STANDIN-ORD-002", existing.getProviderReference());
        assertEquals(Instant.parse("2026-10-14T09:50:32Z"), existing.getAuthorizedAt());
        assertEquals("CUST-002", existing.getCustomerId());
        assertTrue(producer.getCapturedMessages().values().stream().allMatch(List::isEmpty));
    }

    @Test
    void reauthorizePaymentTest() { // TODO: implement this test
    }

    @Test
    void retryPaymentTest() { // TODO: implement this test
    }

    @Test
    void capturePaymentTest() { // C-12: AUTHORIZED -> CAPTURED
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var before = paymentRepository.findByOrderId("ORD-012").orElseThrow();

        var payment = paymentsProcessingService.capturePayment(new CapturePaymentInput().setOrderId("ORD-012"));

        var saved = paymentRepository.findByOrderId("ORD-012").orElseThrow();
        assertEquals(before.getId(), payment.getId());
        assertEquals(PaymentStatus.CAPTURED, saved.getStatus());
        assertEquals(TestClock.NOW, saved.getCapturedAt());
        assertEquals("STANDIN-ORD-012", saved.getProviderReference());
        assertEquals(1, producer.getOnPaymentCapturedCapturedMessages().size());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
        var event = (PaymentCaptured)
                producer.getOnPaymentCapturedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-012", event.getOrderId().toString());
        assertEquals(before.getCustomerEmail(), event.getCustomerEmail().toString());
        assertEquals("STANDIN-ORD-012", event.getProviderReference().toString());
        assertEquals(TestClock.NOW.toString(), event.getCapturedAt().toString());
    }

    @Test
    void capturePaymentFromAuthorizedC20() {
        var payment = paymentsProcessingService.capturePayment(new CapturePaymentInput().setOrderId("ORD-020"));
        assertEquals(PaymentStatus.CAPTURED, payment.getStatus());
        assertEquals(TestClock.NOW, payment.getCapturedAt());
    }

    @Test
    void capturePaymentRedeliveryIsNoOp() { // C-01 and C-22 CAPTURED, C-21 CAPTURE_FAILED, C-04 VOIDED
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        for (var orderId : List.of("ORD-001", "ORD-022", "ORD-021", "ORD-004")) {
            var before = paymentRepository.findByOrderId(orderId).orElseThrow();
            var payment = paymentsProcessingService.capturePayment(new CapturePaymentInput().setOrderId(orderId));
            assertEquals(before.getStatus(), payment.getStatus());
            assertEquals(before.getCapturedAt(), payment.getCapturedAt());
        }
        assertTrue(producer.getCapturedMessages().values().stream().allMatch(List::isEmpty));
    }

    @Test
    void capturePaymentInOtherStateOrNoPaymentThrows() {
        assertThrows(
                RuntimeException.class,
                () -> paymentsProcessingService.capturePayment(new CapturePaymentInput().setOrderId("ORD-003")));
        assertThrows(
                RuntimeException.class,
                () -> paymentsProcessingService.capturePayment(new CapturePaymentInput().setOrderId("ORD-011")));
    }

    @Test
    void voidPaymentTest() { // TODO: implement this test
    }

    @Test
    void getPaymentStatusTest() { // TODO: implement this test
    }

    @Test
    void findByOrderIdTest() {
        var payment = paymentRepository.findByOrderId("ORD-001").orElseThrow();
        assertEquals(1L, payment.getId());
        assertEquals("ORD-001", payment.getOrderId());
        assertEquals(PaymentStatus.CAPTURED, payment.getStatus());
        assertTrue(paymentRepository.findByOrderId("ORD-011").isEmpty());
    }

    @Test
    void clockIsFixedAtTestNow() {
        assertSame(TestClock.FIXED, paymentsProcessingService.clock());
        assertEquals(TestClock.NOW, paymentsProcessingService.clock().instant());
        assertEquals(TestClock.FIXED, new TestClockConfiguration().clock());
    }

    @Test
    void mainClockIsSystemUtc() {
        var clock = new ClockConfiguration().clock();
        assertEquals(ZoneOffset.UTC, clock.getZone());
        assertNotEquals(TestClock.NOW, clock.instant());
    }
}

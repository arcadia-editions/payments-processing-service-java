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
import com.arcadiaeditions.payments.processing.events.avro.PaymentCaptureFailed;
import com.arcadiaeditions.payments.processing.events.avro.PaymentCaptured;
import com.arcadiaeditions.payments.processing.events.avro.PaymentDeclined;
import com.arcadiaeditions.payments.processing.events.avro.PaymentFailed;
import com.arcadiaeditions.payments.processing.events.avro.PaymentRetried;
import com.arcadiaeditions.payments.processing.events.avro.PaymentRetryExhausted;
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
    void authorizePaymentDeclinedTest() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var payment = paymentsProcessingService.authorizePayment(
                newAuthorizePaymentInput("ORD-098").setPaymentMethodId("pm_decline"));

        var saved = paymentRepository.findByOrderId("ORD-098").orElseThrow();
        assertEquals(saved.getId(), payment.getId());
        assertEquals(PaymentStatus.DECLINED, saved.getStatus());
        assertEquals(1, saved.getAttemptCount());
        assertEquals(TestClock.NOW, saved.getDeclinedAt());
        assertEquals("card_declined", saved.getFailureReason());
        assertNull(saved.getAuthorizedAt());
        assertNull(saved.getFailedAt());

        assertEquals(1, producer.getOnPaymentDeclinedCapturedMessages().size());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
        var event = (PaymentDeclined)
                producer.getOnPaymentDeclinedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-098", event.getOrderId().toString());
        assertEquals("CUST-099", event.getCustomerId().toString());
        assertEquals("card_declined", event.getReason().toString());
        assertEquals(TestClock.NOW.toString(), event.getDeclinedAt().toString());
    }

    @Test
    void authorizePaymentFailedTest() { // result shape C-16: FAILED a1, failedAt, failureReason
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var cases = List.of(
                List.of("ORD-097", StandInPaymentProcessor.PM_FAIL_ALWAYS, "processor_unavailable"),
                List.of("ORD-096", StandInPaymentProcessor.PM_FAIL_ONCE, "processor_unavailable"),
                List.of("ORD-095", StandInPaymentProcessor.PM_TIMEOUT, "processor_timeout"));
        for (int i = 0; i < cases.size(); i++) {
            var orderId = cases.get(i).get(0);
            var reason = cases.get(i).get(2);
            var payment = paymentsProcessingService.authorizePayment(newAuthorizePaymentInput(orderId)
                    .setPaymentMethodId(cases.get(i).get(1)));

            var saved = paymentRepository.findByOrderId(orderId).orElseThrow();
            assertEquals(saved.getId(), payment.getId());
            assertEquals(PaymentStatus.FAILED, saved.getStatus());
            assertEquals(1, saved.getAttemptCount());
            assertEquals(TestClock.NOW, saved.getFailedAt());
            assertEquals(reason, saved.getFailureReason());
            assertNull(saved.getAuthorizedAt());
            assertNull(saved.getDeclinedAt());
            assertNull(saved.getProviderReference());

            assertEquals(i + 1, producer.getOnPaymentFailedCapturedMessages().size());
            assertEquals(
                    i + 1,
                    producer.getCapturedMessages().values().stream()
                            .mapToInt(List::size)
                            .sum());
            var event = (PaymentFailed)
                    producer.getOnPaymentFailedCapturedMessages().get(i).getPayload();
            assertEquals(orderId, event.getOrderId().toString());
            assertEquals("CUST-099", event.getCustomerId().toString());
            assertEquals(1, event.getAttemptCount());
            assertEquals(reason, event.getReason().toString());
            assertEquals(TestClock.NOW.toString(), event.getFailedAt().toString());
        }
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
    void reauthorizePaymentTest() { // C-16 a1 to a2, C-17 a2 to a3: stand-in authorizes
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var cases = List.of(List.of("ORD-016", 1), List.of("ORD-017", 2));
        for (int i = 0; i < cases.size(); i++) {
            var orderId = (String) cases.get(i).get(0);
            var attempt = (Integer) cases.get(i).get(1);
            var before = paymentRepository.findByOrderId(orderId).orElseThrow();

            var payment = paymentsProcessingService
                    .reauthorizePayment(
                            new ReauthorizePaymentInput().setOrderId(orderId).setAttemptCount(attempt))
                    .orElseThrow();

            var saved = paymentRepository.findByOrderId(orderId).orElseThrow();
            assertEquals(before.getId(), payment.getId());
            assertEquals(PaymentStatus.AUTHORIZED, saved.getStatus());
            assertEquals(attempt + 1, saved.getAttemptCount());
            assertEquals("STANDIN-" + orderId, saved.getProviderReference());
            assertEquals(TestClock.NOW, saved.getAuthorizedAt());
            assertEquals(
                    i + 1, producer.getOnPaymentAuthorizedCapturedMessages().size());
            assertEquals(
                    i + 1,
                    producer.getCapturedMessages().values().stream()
                            .mapToInt(List::size)
                            .sum());
        }
    }

    @Test
    void reauthorizePaymentFailedAgainTest() { // C-17 a2 to a3 with a processor that always fails
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var before = paymentRepository.findByOrderId("ORD-017").orElseThrow();
        before.setPaymentMethodId(StandInPaymentProcessor.PM_FAIL_ALWAYS);
        paymentRepository.save(before);

        var payment = paymentsProcessingService
                .reauthorizePayment(
                        new ReauthorizePaymentInput().setOrderId("ORD-017").setAttemptCount(2))
                .orElseThrow();

        var saved = paymentRepository.findByOrderId("ORD-017").orElseThrow();
        assertEquals(saved.getId(), payment.getId());
        assertEquals(PaymentStatus.FAILED, saved.getStatus());
        assertEquals(3, saved.getAttemptCount());
        assertEquals(TestClock.NOW, saved.getFailedAt());
        assertEquals("processor_unavailable", saved.getFailureReason());
        assertEquals(1, producer.getOnPaymentFailedCapturedMessages().size());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
        var event = (PaymentFailed)
                producer.getOnPaymentFailedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-017", event.getOrderId().toString());
        assertEquals(3, event.getAttemptCount());
    }

    @Test
    void reauthorizePaymentNoOpTest() { // C-11 none, C-08 wrong state, C-16 stale attemptCount
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        assertTrue(paymentsProcessingService
                .reauthorizePayment(
                        new ReauthorizePaymentInput().setOrderId("ORD-011").setAttemptCount(1))
                .isEmpty());
        for (var c : List.of(List.of("ORD-008", 3), List.of("ORD-016", 2))) {
            var orderId = (String) c.get(0);
            var before = paymentRepository.findByOrderId(orderId).orElseThrow();
            paymentsProcessingService.reauthorizePayment(
                    new ReauthorizePaymentInput().setOrderId(orderId).setAttemptCount((Integer) c.get(1)));
            var after = paymentRepository.findByOrderId(orderId).orElseThrow();
            assertEquals(before.getStatus(), after.getStatus());
            assertEquals(before.getAttemptCount(), after.getAttemptCount());
        }
        assertTrue(producer.getCapturedMessages().values().stream().allMatch(List::isEmpty));
    }

    @Test
    void retryPaymentTest() { // C-16 a1, C-17 a2: PaymentRetried after the backoff, still FAILED
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var sleeps = new ArrayList<Duration>();
        paymentsProcessingService.setSleeper(sleeps::add);
        var cases = List.of(
                List.of("ORD-016", 1, Duration.ZERO), // failed 09:59:50 + 5 s, already due at 10:00:00
                List.of("ORD-017", 2, Duration.ofSeconds(6))); // failed 09:59:51 + 15 s, 6 s left
        for (int i = 0; i < cases.size(); i++) {
            var orderId = (String) cases.get(i).get(0);
            var attempt = (Integer) cases.get(i).get(1);
            var before = paymentRepository.findByOrderId(orderId).orElseThrow();

            var payment = paymentsProcessingService
                    .retryPayment(new RetryPaymentInput().setOrderId(orderId).setAttemptCount(attempt))
                    .orElseThrow();

            var saved = paymentRepository.findByOrderId(orderId).orElseThrow();
            assertEquals(before.getId(), payment.getId());
            assertEquals(PaymentStatus.FAILED, saved.getStatus());
            assertEquals(attempt, saved.getAttemptCount());
            assertEquals(before.getFailedAt(), saved.getFailedAt());
            assertEquals(cases.get(i).get(2), sleeps.get(i));

            assertEquals(i + 1, producer.getOnPaymentRetriedCapturedMessages().size());
            assertEquals(
                    i + 1,
                    producer.getCapturedMessages().values().stream()
                            .mapToInt(List::size)
                            .sum());
            var event = (PaymentRetried)
                    producer.getOnPaymentRetriedCapturedMessages().get(i).getPayload();
            assertEquals(orderId, event.getOrderId().toString());
            assertEquals(before.getCustomerId(), event.getCustomerId().toString());
            assertEquals(attempt, event.getAttemptCount());
            assertEquals(TestClock.NOW.toString(), event.getRetriedAt().toString());
        }
    }

    @Test
    void retryPaymentExhaustedTest() { // C-18 a3: RETRY_EXHAUSTED, no backoff
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var sleeps = new ArrayList<Duration>();
        paymentsProcessingService.setSleeper(sleeps::add);

        var payment = paymentsProcessingService
                .retryPayment(new RetryPaymentInput().setOrderId("ORD-018").setAttemptCount(3))
                .orElseThrow();

        var saved = paymentRepository.findByOrderId("ORD-018").orElseThrow();
        assertEquals(saved.getId(), payment.getId());
        assertEquals(PaymentStatus.RETRY_EXHAUSTED, saved.getStatus());
        assertEquals(3, saved.getAttemptCount());
        assertTrue(sleeps.isEmpty());
        assertEquals(1, producer.getOnPaymentRetryExhaustedCapturedMessages().size());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
        var event = (PaymentRetryExhausted)
                producer.getOnPaymentRetryExhaustedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-018", event.getOrderId().toString());
        assertEquals("CUST-018", event.getCustomerId().toString());
        assertEquals(3, event.getAttemptCount());
        assertEquals("processor_timeout", event.getReason().toString());
        assertEquals(TestClock.NOW.toString(), event.getExhaustedAt().toString());
    }

    @Test
    void retryPaymentNoOpTest() { // C-11 none, C-03 DECLINED, C-08 RETRY_EXHAUSTED, C-16 stale attemptCount
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var sleeps = new ArrayList<Duration>();
        paymentsProcessingService.setSleeper(sleeps::add);
        assertTrue(paymentsProcessingService
                .retryPayment(new RetryPaymentInput().setOrderId("ORD-011").setAttemptCount(1))
                .isEmpty());
        for (var c : List.of(List.of("ORD-003", 1), List.of("ORD-008", 3), List.of("ORD-016", 2))) {
            var orderId = (String) c.get(0);
            var before = paymentRepository.findByOrderId(orderId).orElseThrow();
            var payment = paymentsProcessingService.retryPayment(
                    new RetryPaymentInput().setOrderId(orderId).setAttemptCount((Integer) c.get(1)));
            var after = paymentRepository.findByOrderId(orderId).orElseThrow();
            assertEquals(before.getStatus(), after.getStatus());
            assertEquals(before.getAttemptCount(), after.getAttemptCount());
            assertEquals(before.getStatus(), payment.map(Payment::getStatus).orElse(before.getStatus()));
        }
        assertTrue(sleeps.isEmpty());
        assertTrue(producer.getCapturedMessages().values().stream().allMatch(List::isEmpty));
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
    void capturePaymentFailedTest() { // C-12 with a refusing processor: AUTHORIZED -> CAPTURE_FAILED
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var before = paymentRepository.findByOrderId("ORD-012").orElseThrow();
        before.setProviderReference("STANDIN-CAPFAIL-ORD-012");
        paymentRepository.save(before);

        var payment = paymentsProcessingService.capturePayment(new CapturePaymentInput().setOrderId("ORD-012"));

        var saved = paymentRepository.findByOrderId("ORD-012").orElseThrow();
        assertEquals(before.getId(), payment.getId());
        assertEquals(PaymentStatus.CAPTURE_FAILED, saved.getStatus());
        assertEquals("capture_rejected", saved.getFailureReason());
        assertNull(saved.getFailedAt());
        assertNull(saved.getCapturedAt());
        assertTrue(producer.getOnPaymentCapturedCapturedMessages().isEmpty());
        assertEquals(1, producer.getOnPaymentCaptureFailedCapturedMessages().size());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
        var event = (PaymentCaptureFailed)
                producer.getOnPaymentCaptureFailedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-012", event.getOrderId().toString());
        assertEquals("capture_rejected", event.getReason().toString());
        assertEquals(TestClock.NOW.toString(), event.getFailedAt().toString());
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
    void voidPaymentTest() { // C-21 from CAPTURE_FAILED, C-02 from AUTHORIZED
        for (var orderId : List.of("ORD-021", "ORD-002")) {
            context.reloadTestData();
            var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
            var before = paymentRepository.findByOrderId(orderId).orElseThrow();
            var eventsBefore = producer.getOnPaymentVoidedCapturedMessages().size();

            var result = paymentsProcessingService.voidPayment(
                    new VoidPaymentInput().setOrderId(orderId).setReason("fulfillment_failed"));

            var saved = paymentRepository.findByOrderId(orderId).orElseThrow();
            assertEquals(before.getId(), result.orElseThrow().getId());
            assertEquals(PaymentStatus.VOIDED, saved.getStatus());
            assertEquals(TestClock.NOW, saved.getVoidedAt());
            assertNull(saved.getVoidFailedAt());
            assertEquals("fulfillment_failed", saved.getFailureReason());
            assertTrue(producer.getOnPaymentVoidFailedCapturedMessages().isEmpty());
            assertEquals(
                    eventsBefore + 1,
                    producer.getOnPaymentVoidedCapturedMessages().size());
            assertEquals(
                    eventsBefore + 1,
                    producer.getCapturedMessages().values().stream()
                            .mapToInt(List::size)
                            .sum());
            var event = (com.arcadiaeditions.payments.processing.events.avro.PaymentVoided)
                    producer.getOnPaymentVoidedCapturedMessages()
                            .get(eventsBefore)
                            .getPayload();
            assertEquals(orderId, event.getOrderId().toString());
            assertEquals(
                    before.getProviderReference(), event.getProviderReference().toString());
            assertEquals("fulfillment_failed", event.getReason().toString());
            assertEquals(TestClock.NOW.toString(), event.getVoidedAt().toString());
        }
    }

    @Test
    void voidPaymentRefusedTest() { // C-02 with pm_void_fail: AUTHORIZED -> VOID_FAILED
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var before = paymentRepository.findByOrderId("ORD-002").orElseThrow();
        before.setPaymentMethodId("pm_void_fail");
        paymentRepository.save(before);

        paymentsProcessingService.voidPayment(
                new VoidPaymentInput().setOrderId("ORD-002").setReason("order_cancelled"));

        var saved = paymentRepository.findByOrderId("ORD-002").orElseThrow();
        assertEquals(PaymentStatus.VOID_FAILED, saved.getStatus());
        assertEquals(TestClock.NOW, saved.getVoidFailedAt());
        assertNull(saved.getVoidedAt());
        assertEquals("order_cancelled", saved.getFailureReason());
        assertTrue(producer.getOnPaymentVoidedCapturedMessages().isEmpty());
        assertEquals(1, producer.getOnPaymentVoidFailedCapturedMessages().size());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
        var event = (com.arcadiaeditions.payments.processing.events.avro.PaymentVoidFailed)
                producer.getOnPaymentVoidFailedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-002", event.getOrderId().toString());
        assertEquals("order_cancelled", event.getReason().toString());
        assertEquals(TestClock.NOW.toString(), event.getFailedAt().toString());
    }

    @Test
    void voidPaymentNoOpTest() { // C-15 AUTHORIZATION_REQUESTED, C-09 VOIDED, C-10 VOID_FAILED, C-11 none
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var expected = new java.util.LinkedHashMap<String, java.util.Optional<PaymentStatus>>();
        expected.put("ORD-015", java.util.Optional.of(PaymentStatus.AUTHORIZATION_REQUESTED));
        expected.put("ORD-009", java.util.Optional.of(PaymentStatus.VOIDED));
        expected.put("ORD-010", java.util.Optional.of(PaymentStatus.VOID_FAILED));
        expected.put("ORD-011", java.util.Optional.empty());
        for (var entry : expected.entrySet()) {
            var orderId = entry.getKey();
            var voidedAt =
                    paymentRepository.findByOrderId(orderId).map(Payment::getVoidedAt); // copied, not a reference
            var voidFailedAt = paymentRepository.findByOrderId(orderId).map(Payment::getVoidFailedAt);
            var result = paymentsProcessingService.voidPayment(
                    new VoidPaymentInput().setOrderId(orderId).setReason("fulfillment_failed"));
            assertEquals(entry.getValue(), result.map(Payment::getStatus));
            var after = paymentRepository.findByOrderId(orderId);
            assertEquals(entry.getValue(), after.map(Payment::getStatus));
            assertEquals(voidedAt, after.map(Payment::getVoidedAt));
            assertEquals(voidFailedAt, after.map(Payment::getVoidFailedAt));
            assertTrue(after.map(p -> !"fulfillment_failed".equals(p.getFailureReason()))
                    .orElse(true));
        }
        assertTrue(producer.getCapturedMessages().values().stream().allMatch(List::isEmpty));
    }

    @Test
    void getPaymentStatusTest() {
        var none = paymentsProcessingService.getPaymentStatus(new GetPaymentStatusInput().setOrderId("ORD-011"));
        assertEquals(GetPaymentStatusOutcome.NO_PAYMENT, none.getOutcome());
        assertEquals("ORD-011", none.getNoPayment().getOrderId());
        assertNull(none.getStarted());

        var captured = paymentsProcessingService.getPaymentStatus(new GetPaymentStatusInput().setOrderId("ORD-001"));
        assertEquals(GetPaymentStatusOutcome.STARTED, captured.getOutcome());
        assertEquals("ORD-001", captured.getStarted().getOrderId());
        assertEquals(PaymentStatus.CAPTURED, captured.getStarted().getStatus());
        assertNull(captured.getNoPayment());

        var inFlight = paymentsProcessingService.getPaymentStatus(new GetPaymentStatusInput().setOrderId("ORD-015"));
        assertEquals(GetPaymentStatusOutcome.STARTED, inFlight.getOutcome());
        assertEquals(
                PaymentStatus.AUTHORIZATION_REQUESTED, inFlight.getStarted().getStatus());
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

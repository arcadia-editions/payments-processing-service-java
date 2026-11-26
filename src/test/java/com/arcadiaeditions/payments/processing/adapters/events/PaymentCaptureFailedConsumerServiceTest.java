package com.arcadiaeditions.payments.processing.adapters.events;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadiaeditions.payments.processing.asyncapi.provider.IPaymentCaptureFailedEventV1ConsumerService;
import com.arcadiaeditions.payments.processing.config.ServicesInMemoryConfig;
import com.arcadiaeditions.payments.processing.config.TestClock;
import com.arcadiaeditions.payments.processing.core.domain.PaymentStatus;
import com.arcadiaeditions.payments.processing.events.avro.PaymentCaptureFailed;
import com.arcadiaeditions.payments.processing.events.avro.PaymentVoided;
import com.arcadiaeditions.payments.processing.infrastructure.jpa.inmemory.PaymentRepositoryInMemory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * payment-capture-failed-event-v1 -> voidPayment. Event message only. In-memory service, no Spring context.
 */
class PaymentCaptureFailedConsumerServiceTest {

    ServicesInMemoryConfig context = new ServicesInMemoryConfig();

    PaymentCaptureFailedConsumerService consumer =
            new PaymentCaptureFailedConsumerService(context.paymentsProcessingService());

    PaymentRepositoryInMemory paymentRepository = context.paymentRepository();

    @BeforeEach
    void setUp() {
        context.reloadTestData();
    }

    /**
     * C-21: PaymentCaptureFailed carries orderId and reason. customerId and failedAt have no target. ORD-021 is
     * CAPTURE_FAILED; the stand-in voids it and emits only PaymentVoided. failureReason becomes the mapped reason.
     */
    @Test
    void paymentCaptureFailedVoidsCaptureFailedPayment() {
        var payload = new PaymentCaptureFailed("ORD-021", "CUST-OTHER", "capture_rejected", "2026-01-01T00:00:00Z");

        consumer.doPaymentCaptureFailed(
                payload, new IPaymentCaptureFailedEventV1ConsumerService.PaymentCaptureFailedHeaders());

        assertVoidedOrd021();
    }

    /** C-21 redelivery: a second PaymentCaptureFailed after VOIDED emits nothing more. */
    @Test
    void paymentCaptureFailedRedeliveryIsNoOp() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var payload = new PaymentCaptureFailed("ORD-021", "CUST-021", "capture_rejected", "2026-10-14T09:55:00Z");
        var headers = new IPaymentCaptureFailedEventV1ConsumerService.PaymentCaptureFailedHeaders();

        consumer.doPaymentCaptureFailed(payload, headers);
        consumer.doPaymentCaptureFailed(payload, headers);

        var saved = paymentRepository.findByOrderId("ORD-021").orElseThrow();
        assertEquals(PaymentStatus.VOIDED, saved.getStatus());
        assertEquals(TestClock.NOW, saved.getVoidedAt());
        assertNull(saved.getVoidFailedAt());
        assertEquals("capture_rejected", saved.getFailureReason());
        assertEquals("CUST-021", saved.getCustomerId());
        assertEquals(1, producer.getOnPaymentVoidedCapturedMessages().size());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
    }

    /**
     * C-09 is already VOIDED. The message reason is not the fixture reason: a real call would overwrite
     * failureReason. Fixed fixture values, because the in-memory repo returns the same instance.
     */
    @Test
    void paymentCaptureFailedOnVoidedPaymentIsNoOp() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var payload = new PaymentCaptureFailed("ORD-009", "CUST-OTHER", "fulfillment_failed", "2026-01-01T00:00:00Z");

        consumer.doPaymentCaptureFailed(
                payload, new IPaymentCaptureFailedEventV1ConsumerService.PaymentCaptureFailedHeaders());

        var saved = paymentRepository.findByOrderId("ORD-009").orElseThrow();
        assertEquals(PaymentStatus.VOIDED, saved.getStatus());
        assertEquals(Instant.parse("2026-10-14T09:51:37Z"), saved.getVoidedAt());
        assertNull(saved.getVoidFailedAt());
        assertEquals("capture_rejected", saved.getFailureReason());
        assertEquals("CUST-009", saved.getCustomerId());
        assertEquals(Instant.parse("2026-10-14T09:50:29Z"), saved.getAuthorizedAt());
        assertTrue(producer.getCapturedMessages().values().stream().allMatch(List::isEmpty));
    }

    /** C-11: no Payment for ORD-011. The event message is a no-op. */
    @Test
    void paymentCaptureFailedUnknownOrderIsNoOp() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var count = paymentRepository.count();
        var payload = new PaymentCaptureFailed("ORD-011", "CUST-011", "capture_rejected", "2026-10-14T09:55:00Z");

        consumer.doPaymentCaptureFailed(
                payload, new IPaymentCaptureFailedEventV1ConsumerService.PaymentCaptureFailedHeaders());

        assertEquals(count, paymentRepository.count());
        assertTrue(paymentRepository.findByOrderId("ORD-011").isEmpty());
        assertTrue(producer.getCapturedMessages().values().stream().allMatch(List::isEmpty));
    }

    private void assertVoidedOrd021() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var saved = paymentRepository.findByOrderId("ORD-021").orElseThrow();
        assertEquals(PaymentStatus.VOIDED, saved.getStatus());
        assertEquals(TestClock.NOW, saved.getVoidedAt());
        assertNull(saved.getVoidFailedAt());
        assertEquals("capture_rejected", saved.getFailureReason());
        assertEquals("CUST-021", saved.getCustomerId());
        assertEquals("STANDIN-ORD-021", saved.getProviderReference());
        assertEquals(Instant.parse("2026-10-14T09:54:46Z"), saved.getAuthorizedAt());
        assertNull(saved.getFailedAt());
        assertEquals(1, saved.getAttemptCount());

        assertEquals(1, producer.getOnPaymentVoidedCapturedMessages().size());
        assertTrue(producer.getOnPaymentVoidFailedCapturedMessages().isEmpty());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
        var event = (PaymentVoided)
                producer.getOnPaymentVoidedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-021", event.getOrderId().toString());
        assertEquals("CUST-021", event.getCustomerId().toString());
        assertEquals("STANDIN-ORD-021", event.getProviderReference().toString());
        assertEquals("capture_rejected", event.getReason().toString());
        assertEquals(TestClock.NOW.toString(), event.getVoidedAt().toString());
    }
}

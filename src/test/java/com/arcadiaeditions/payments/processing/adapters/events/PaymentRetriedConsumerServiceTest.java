package com.arcadiaeditions.payments.processing.adapters.events;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadiaeditions.payments.processing.asyncapi.provider.IPaymentRetriedEventV1ConsumerService;
import com.arcadiaeditions.payments.processing.config.ServicesInMemoryConfig;
import com.arcadiaeditions.payments.processing.config.TestClock;
import com.arcadiaeditions.payments.processing.core.domain.PaymentStatus;
import com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized;
import com.arcadiaeditions.payments.processing.events.avro.PaymentRetried;
import com.arcadiaeditions.payments.processing.infrastructure.jpa.inmemory.PaymentRepositoryInMemory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * payment-retried-event-v1 -> reauthorizePayment. Event message only. In-memory service, no Spring context.
 */
class PaymentRetriedConsumerServiceTest {

    ServicesInMemoryConfig context = new ServicesInMemoryConfig();

    PaymentRetriedConsumerService consumer = new PaymentRetriedConsumerService(context.paymentsProcessingService());

    PaymentRepositoryInMemory paymentRepository = context.paymentRepository();

    @BeforeEach
    void setUp() {
        context.reloadTestData();
    }

    /**
     * C-16: PaymentRetried carries orderId and attemptCount. customerId and retriedAt have no target. ORD-016 is
     * FAILED a1; the stand-in authorizes attempt 2 and emits only PaymentAuthorized.
     */
    @Test
    void paymentRetriedReauthorizesFailedPayment() {
        var payload = new PaymentRetried("ORD-016", "CUST-OTHER", 1, "2026-01-01T00:00:00Z");

        consumer.doPaymentRetried(payload, new IPaymentRetriedEventV1ConsumerService.PaymentRetriedHeaders());

        assertAuthorizedOrd016();
    }

    /** C-16 redelivery: a second PaymentRetried after AUTHORIZED emits nothing more. */
    @Test
    void paymentRetriedRedeliveryIsNoOp() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var payload = new PaymentRetried("ORD-016", "CUST-016", 1, "2026-10-14T10:00:00Z");
        var headers = new IPaymentRetriedEventV1ConsumerService.PaymentRetriedHeaders();

        consumer.doPaymentRetried(payload, headers);
        var authorizedAt =
                paymentRepository.findByOrderId("ORD-016").orElseThrow().getAuthorizedAt();

        consumer.doPaymentRetried(payload, headers);

        var saved = paymentRepository.findByOrderId("ORD-016").orElseThrow();
        assertEquals(PaymentStatus.AUTHORIZED, saved.getStatus());
        assertEquals(2, saved.getAttemptCount());
        assertEquals(authorizedAt, saved.getAuthorizedAt());
        assertEquals(1, producer.getOnPaymentAuthorizedCapturedMessages().size());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
    }

    /** C-16 stale attemptCount 2: no-op, no event. */
    @Test
    void paymentRetriedStaleAttemptCountIsNoOp() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var before = paymentRepository.findByOrderId("ORD-016").orElseThrow();
        var payload = new PaymentRetried("ORD-016", "CUST-016", 2, "2026-10-14T10:00:00Z");

        consumer.doPaymentRetried(payload, new IPaymentRetriedEventV1ConsumerService.PaymentRetriedHeaders());

        var after = paymentRepository.findByOrderId("ORD-016").orElseThrow();
        assertEquals(before.getStatus(), after.getStatus());
        assertEquals(before.getAttemptCount(), after.getAttemptCount());
        assertEquals(PaymentStatus.FAILED, after.getStatus());
        assertTrue(producer.getCapturedMessages().values().stream().allMatch(List::isEmpty));
    }

    /** C-11: no Payment for ORD-011. The event message is a no-op. */
    @Test
    void paymentRetriedUnknownOrderIsNoOp() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var count = paymentRepository.count();
        var payload = new PaymentRetried("ORD-011", "CUST-011", 1, "2026-10-14T10:00:00Z");

        consumer.doPaymentRetried(payload, new IPaymentRetriedEventV1ConsumerService.PaymentRetriedHeaders());

        assertEquals(count, paymentRepository.count());
        assertTrue(paymentRepository.findByOrderId("ORD-011").isEmpty());
        assertTrue(producer.getCapturedMessages().values().stream().allMatch(List::isEmpty));
    }

    private void assertAuthorizedOrd016() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var saved = paymentRepository.findByOrderId("ORD-016").orElseThrow();
        assertEquals(PaymentStatus.AUTHORIZED, saved.getStatus());
        assertEquals(2, saved.getAttemptCount());
        assertEquals("STANDIN-ORD-016", saved.getProviderReference());
        assertEquals(TestClock.NOW, saved.getAuthorizedAt());
        assertEquals("CUST-016", saved.getCustomerId());
        assertEquals(Instant.parse("2026-10-14T09:59:50Z"), saved.getFailedAt());

        assertEquals(1, producer.getOnPaymentAuthorizedCapturedMessages().size());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
        var event = (PaymentAuthorized)
                producer.getOnPaymentAuthorizedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-016", event.getOrderId().toString());
        assertEquals("CUST-016", event.getCustomerId().toString());
        assertEquals("STANDIN-ORD-016", event.getProviderReference().toString());
        assertEquals(TestClock.NOW.toString(), event.getAuthorizedAt().toString());
    }
}

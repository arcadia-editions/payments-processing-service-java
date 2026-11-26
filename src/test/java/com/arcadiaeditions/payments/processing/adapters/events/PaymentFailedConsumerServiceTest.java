package com.arcadiaeditions.payments.processing.adapters.events;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadiaeditions.payments.processing.asyncapi.provider.IPaymentFailedEventV1ConsumerService;
import com.arcadiaeditions.payments.processing.config.ServicesInMemoryConfig;
import com.arcadiaeditions.payments.processing.config.TestClock;
import com.arcadiaeditions.payments.processing.core.application.PaymentsProcessingServiceImpl;
import com.arcadiaeditions.payments.processing.core.domain.PaymentStatus;
import com.arcadiaeditions.payments.processing.events.avro.PaymentFailed;
import com.arcadiaeditions.payments.processing.events.avro.PaymentRetried;
import com.arcadiaeditions.payments.processing.events.avro.RetryPaymentInput;
import com.arcadiaeditions.payments.processing.infrastructure.jpa.inmemory.PaymentRepositoryInMemory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * payment-failed-event-v1 -> retryPayment. Both channel messages. In-memory service, no Spring context.
 */
class PaymentFailedConsumerServiceTest {

    ServicesInMemoryConfig context = new ServicesInMemoryConfig();

    PaymentsProcessingServiceImpl service = context.paymentsProcessingService();

    PaymentFailedConsumerService consumer = new PaymentFailedConsumerService(service);

    PaymentRepositoryInMemory paymentRepository = context.paymentRepository();

    List<Duration> sleeps = new ArrayList<>();

    @BeforeEach
    void setUp() {
        context.reloadTestData();
        sleeps.clear();
        service.setSleeper(sleeps::add);
    }

    /**
     * C-16: PaymentFailed carries orderId and attemptCount. customerId, reason and failedAt have no target.
     * ORD-016 is FAILED a1, already due (failed 09:59:50 + 5 s), so the backoff is zero. Status stays FAILED and
     * the only event is PaymentRetried.
     */
    @Test
    void paymentFailedRetriesFailedPayment() {
        var payload = new PaymentFailed("ORD-016", "CUST-OTHER", 1, "other_reason", "2026-01-01T00:00:00Z");

        consumer.doRetryPayment(payload, new IPaymentFailedEventV1ConsumerService.PaymentFailedHeaders());

        assertRetriedOrd016();
    }

    /** C-16 through the command message: only orderId and attemptCount. Same outcome. */
    @Test
    void retryPaymentInputRetriesFailedPayment() {
        var payload = new RetryPaymentInput("ORD-016", 1);

        consumer.doRetryPayment(payload, new IPaymentFailedEventV1ConsumerService.RetryPaymentInputHeaders());

        assertRetriedOrd016();
    }

    /** C-16 stale attemptCount 2: no-op, no event, no backoff. */
    @Test
    void paymentFailedStaleAttemptCountIsNoOp() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var before = paymentRepository.findByOrderId("ORD-016").orElseThrow();
        var payload = new PaymentFailed("ORD-016", "CUST-016", 2, "processor_unavailable", "2026-10-14T09:59:50Z");

        consumer.doRetryPayment(payload, new IPaymentFailedEventV1ConsumerService.PaymentFailedHeaders());

        var after = paymentRepository.findByOrderId("ORD-016").orElseThrow();
        assertEquals(before.getStatus(), after.getStatus());
        assertEquals(before.getAttemptCount(), after.getAttemptCount());
        assertEquals(before.getFailedAt(), after.getFailedAt());
        assertTrue(sleeps.isEmpty());
        assertTrue(producer.getCapturedMessages().values().stream().allMatch(List::isEmpty));
    }

    /** C-11: no Payment for ORD-011. The command message is a no-op. */
    @Test
    void retryPaymentInputUnknownOrderIsNoOp() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var count = paymentRepository.count();
        var payload = new RetryPaymentInput("ORD-011", 1);

        consumer.doRetryPayment(payload, new IPaymentFailedEventV1ConsumerService.RetryPaymentInputHeaders());

        assertEquals(count, paymentRepository.count());
        assertTrue(paymentRepository.findByOrderId("ORD-011").isEmpty());
        assertTrue(sleeps.isEmpty());
        assertTrue(producer.getCapturedMessages().values().stream().allMatch(List::isEmpty));
    }

    private void assertRetriedOrd016() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var saved = paymentRepository.findByOrderId("ORD-016").orElseThrow();
        assertEquals(PaymentStatus.FAILED, saved.getStatus());
        assertEquals(1, saved.getAttemptCount());
        assertEquals("CUST-016", saved.getCustomerId());
        assertEquals("processor_unavailable", saved.getFailureReason());
        assertEquals(Instant.parse("2026-10-14T09:59:50Z"), saved.getFailedAt());
        assertEquals(List.of(Duration.ZERO), sleeps);

        assertEquals(1, producer.getOnPaymentRetriedCapturedMessages().size());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
        var event = (PaymentRetried)
                producer.getOnPaymentRetriedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-016", event.getOrderId().toString());
        assertEquals("CUST-016", event.getCustomerId().toString());
        assertEquals(1, event.getAttemptCount());
        assertEquals(TestClock.NOW.toString(), event.getRetriedAt().toString());
    }
}

package com.arcadiaeditions.payments.processing.adapters.events;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadiaeditions.fulfillment.shipping.events.avro.FulfillmentScheduled;
import com.arcadiaeditions.payments.processing.asyncapi.client.IFulfillmentScheduledEventV1ConsumerService;
import com.arcadiaeditions.payments.processing.config.ServicesInMemoryConfig;
import com.arcadiaeditions.payments.processing.config.TestClock;
import com.arcadiaeditions.payments.processing.core.domain.PaymentStatus;
import com.arcadiaeditions.payments.processing.events.avro.PaymentCaptured;
import com.arcadiaeditions.payments.processing.infrastructure.jpa.inmemory.PaymentRepositoryInMemory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FulfillmentScheduled -> capturePayment. In-memory service, no Spring context.
 */
class FulfillmentScheduledConsumerServiceTest {

    ServicesInMemoryConfig context = new ServicesInMemoryConfig();

    FulfillmentScheduledConsumerService consumer =
            new FulfillmentScheduledConsumerService(context.paymentsProcessingService());

    PaymentRepositoryInMemory paymentRepository = context.paymentRepository();

    @BeforeEach
    void setUp() {
        context.reloadTestData();
    }

    /**
     * C-12: FulfillmentScheduled.orderId is the only field capturePayment reads. ORD-012 is AUTHORIZED, so the ZFL
     * captured outcome persists CAPTURED and emits only PaymentCaptured.
     */
    @Test
    void fulfillmentScheduledCapturesAuthorizedPayment() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var payload = new FulfillmentScheduled("FUL-012", "ORD-012", "CUST-OTHER", "2026-10-14T09:55:15Z");

        consumer.onFulfillmentScheduledEventV1(
                payload, new IFulfillmentScheduledEventV1ConsumerService.FulfillmentScheduledHeaders());

        var saved = paymentRepository.findByOrderId("ORD-012").orElseThrow();
        assertEquals(PaymentStatus.CAPTURED, saved.getStatus());
        assertEquals(TestClock.NOW, saved.getCapturedAt());
        assertEquals("STANDIN-ORD-012", saved.getProviderReference());
        assertEquals("CUST-012", saved.getCustomerId());
        assertEquals("customer012@example.com", saved.getCustomerEmail());
        assertEquals(Instant.parse("2026-10-14T09:50:32Z"), saved.getAuthorizedAt());

        assertEquals(1, producer.getOnPaymentCapturedCapturedMessages().size());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
        var event = (PaymentCaptured)
                producer.getOnPaymentCapturedCapturedMessages().get(0).getPayload();
        assertEquals("ORD-012", event.getOrderId().toString());
        assertEquals("customer012@example.com", event.getCustomerEmail().toString());
        assertEquals("STANDIN-ORD-012", event.getProviderReference().toString());
        assertEquals(TestClock.NOW.toString(), event.getCapturedAt().toString());
    }

    /** C-12 redelivery: a second FulfillmentScheduled for the same order emits nothing more. */
    @Test
    void fulfillmentScheduledRedeliveryIsNoOp() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var payload = new FulfillmentScheduled("FUL-012", "ORD-012", "CUST-012", "2026-10-14T09:55:15Z");
        var headers = new IFulfillmentScheduledEventV1ConsumerService.FulfillmentScheduledHeaders();

        consumer.onFulfillmentScheduledEventV1(payload, headers);
        var capturedAt =
                paymentRepository.findByOrderId("ORD-012").orElseThrow().getCapturedAt();

        consumer.onFulfillmentScheduledEventV1(payload, headers);

        var saved = paymentRepository.findByOrderId("ORD-012").orElseThrow();
        assertEquals(PaymentStatus.CAPTURED, saved.getStatus());
        assertEquals(capturedAt, saved.getCapturedAt());
        assertEquals(1, producer.getOnPaymentCapturedCapturedMessages().size());
        assertEquals(
                1,
                producer.getCapturedMessages().values().stream()
                        .mapToInt(List::size)
                        .sum());
    }
}

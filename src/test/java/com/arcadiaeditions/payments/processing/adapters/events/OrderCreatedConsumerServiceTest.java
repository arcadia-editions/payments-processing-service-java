package com.arcadiaeditions.payments.processing.adapters.events;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadiaeditions.orders.checkout.events.avro.OrderCreated;
import com.arcadiaeditions.payments.processing.asyncapi.client.IOrderCreatedEventV1ConsumerService;
import com.arcadiaeditions.payments.processing.config.ServicesInMemoryConfig;
import com.arcadiaeditions.payments.processing.config.TestClock;
import com.arcadiaeditions.payments.processing.core.domain.PaymentStatus;
import com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized;
import com.arcadiaeditions.payments.processing.infrastructure.jpa.inmemory.PaymentRepositoryInMemory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * OrderCreated -> authorizePayment. In-memory service, no Spring context.
 */
class OrderCreatedConsumerServiceTest {

    ServicesInMemoryConfig context = new ServicesInMemoryConfig();

    OrderCreatedConsumerService consumer = new OrderCreatedConsumerService(context.paymentsProcessingService());

    PaymentRepositoryInMemory paymentRepository = context.paymentRepository();

    @BeforeEach
    void setUp() {
        context.reloadTestData();
    }

    /** Creation: every OrderCreated field lands on the payment, and the ZFL authorized outcome emits only PaymentAuthorized. */
    @Test
    void orderCreatedAuthorizesNewPayment() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var payload = new OrderCreated("ORD-099", 0, "CUST-099", "customer099@example.com", "PM-099", 29.99d, "EUR");

        consumer.onOrderCreatedEventV1(payload, new IOrderCreatedEventV1ConsumerService.OrderCreatedHeaders());

        var saved = paymentRepository.findByOrderId("ORD-099").orElseThrow();
        assertEquals(PaymentStatus.AUTHORIZED, saved.getStatus());
        assertEquals(1, saved.getAttemptCount());
        assertEquals("STANDIN-ORD-099", saved.getProviderReference());
        assertEquals(TestClock.NOW, saved.getAuthorizedAt());
        assertEquals("CUST-099", saved.getCustomerId());
        assertEquals("customer099@example.com", saved.getCustomerEmail());
        assertEquals("PM-099", saved.getPaymentMethodId());
        assertEquals(0, new BigDecimal("29.99").compareTo(saved.getAmount()));
        assertEquals("EUR", saved.getCurrency());

        assertEquals(1, producer.getOnPaymentAuthorizedCapturedMessages().size());
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

    /** C-02: ORD-002 is already AUTHORIZED. A second OrderCreated is a no-op and emits nothing. */
    @Test
    void orderCreatedRedeliveryIsNoOp() {
        var producer = context.getEventsProducerInMemoryContext().paymentsProcessingEventsProducer();
        var count = paymentRepository.count();
        var payload = new OrderCreated("ORD-002", 1, "CUST-002", "customer002@example.com", "PM-002", 99.97d, "EUR");

        consumer.onOrderCreatedEventV1(payload, new IOrderCreatedEventV1ConsumerService.OrderCreatedHeaders());

        assertEquals(count, paymentRepository.count());
        var existing = paymentRepository.findByOrderId("ORD-002").orElseThrow();
        assertEquals(PaymentStatus.AUTHORIZED, existing.getStatus());
        assertEquals(1, existing.getAttemptCount());
        assertEquals("STANDIN-ORD-002", existing.getProviderReference());
        assertEquals(Instant.parse("2026-10-14T09:50:32Z"), existing.getAuthorizedAt());
        assertEquals("CUST-002", existing.getCustomerId());
        assertEquals("customer002@example.com", existing.getCustomerEmail());
        assertEquals("PM-002", existing.getPaymentMethodId());
        assertEquals(0, new BigDecimal("99.97").compareTo(existing.getAmount()));
        assertEquals("EUR", existing.getCurrency());
        assertTrue(producer.getCapturedMessages().values().stream().allMatch(List::isEmpty));
    }
}

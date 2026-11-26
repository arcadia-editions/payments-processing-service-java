package com.arcadiaeditions.payments.processing.infrastructure.jpa;

import com.arcadiaeditions.payments.processing.core.domain.*;
import com.arcadiaeditions.payments.processing.core.outbound.jpa.PaymentRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentRepositoryIntegrationTest extends BaseRepositoryIntegrationTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    PaymentRepository paymentRepository;

    @Test
    void findAllTest() {
        var results = paymentRepository.findAll();
        Assertions.assertFalse(results.isEmpty());
    }

    @Test
    void findByIdTest() {
        var id = 1L;
        var payment = paymentRepository.findById(id).orElseThrow();
        Assertions.assertNotNull(payment.getId());
        Assertions.assertNotNull(payment.getVersion());
    }

    @Test
    void findByOrderIdTest() {
        // C-01: the captured payment for ORD-001.
        var payment = paymentRepository.findByOrderId("ORD-001").orElseThrow();
        Assertions.assertEquals(1L, payment.getId());
        Assertions.assertEquals("ORD-001", payment.getOrderId());
        Assertions.assertEquals("CUST-001", payment.getCustomerId());
        Assertions.assertEquals(PaymentStatus.CAPTURED, payment.getStatus());
        Assertions.assertEquals(0, new BigDecimal("49.99").compareTo(payment.getAmount()));

        // C-11: no Payment row for ORD-011.
        Assertions.assertTrue(paymentRepository.findByOrderId("ORD-011").isEmpty());
    }

    @Test
    void saveTest() {
        Payment payment = new Payment();
        payment.setOrderId("");
        payment.setCustomerId("");
        payment.setPaymentMethodId("");
        payment.setCustomerEmail("");
        payment.setAmount(BigDecimal.valueOf(0));
        payment.setCurrency("");
        payment.setStatus(PaymentStatus.values()[0]);
        payment.setProviderReference("");
        payment.setAttemptCount(0);
        payment.setAuthorizedAt(Instant.now());
        payment.setDeclinedAt(Instant.now());
        payment.setFailedAt(Instant.now());
        payment.setCapturedAt(Instant.now());
        payment.setVoidedAt(Instant.now());
        payment.setVoidFailedAt(Instant.now());
        payment.setFailureReason("");

        // Persist aggregate root
        var created = paymentRepository.save(payment);

        // reloading to get relationships persisted by id
        entityManager.flush();
        entityManager.refresh(created);
        Assertions.assertNotNull(created.getId());
        Assertions.assertNotNull(created.getVersion());
    }

    @Test
    void updateTest() {
        var id = 1L;
        // Fixed test now from data.sql. A whole second survives PostgreSQL microsecond truncation.
        var at = Instant.parse("2026-10-14T10:00:00Z");
        var payment = paymentRepository.findById(id).orElseThrow();
        payment.setOrderId("");
        payment.setCustomerId("");
        payment.setPaymentMethodId("");
        payment.setCustomerEmail("");
        payment.setAmount(BigDecimal.valueOf(0));
        payment.setCurrency("");
        payment.setStatus(PaymentStatus.values()[0]);
        payment.setProviderReference("");
        payment.setAttemptCount(0);
        payment.setAuthorizedAt(at);
        payment.setDeclinedAt(at);
        payment.setFailedAt(at);
        payment.setCapturedAt(at);
        payment.setVoidedAt(at);
        payment.setVoidFailedAt(at);
        payment.setFailureReason("");

        payment = paymentRepository.save(payment);
        Assertions.assertEquals("", payment.getOrderId());
        Assertions.assertEquals("", payment.getCustomerId());
        Assertions.assertEquals("", payment.getPaymentMethodId());
        Assertions.assertEquals("", payment.getCustomerEmail());
        Assertions.assertEquals(BigDecimal.valueOf(0), payment.getAmount());
        Assertions.assertEquals("", payment.getCurrency());
        Assertions.assertEquals(PaymentStatus.values()[0], payment.getStatus());
        Assertions.assertEquals("", payment.getProviderReference());
        Assertions.assertEquals(0, payment.getAttemptCount());
        Assertions.assertEquals(at, payment.getAuthorizedAt());
        Assertions.assertEquals(at, payment.getDeclinedAt());
        Assertions.assertEquals(at, payment.getFailedAt());
        Assertions.assertEquals(at, payment.getCapturedAt());
        Assertions.assertEquals(at, payment.getVoidedAt());
        Assertions.assertEquals(at, payment.getVoidFailedAt());
        Assertions.assertEquals("", payment.getFailureReason());
    }

    @Test
    void deleteTest() {
        var id = 1L;
        paymentRepository.deleteById(id);
        var notFound = paymentRepository.findById(id);
        Assertions.assertFalse(notFound.isPresent());
    }
}

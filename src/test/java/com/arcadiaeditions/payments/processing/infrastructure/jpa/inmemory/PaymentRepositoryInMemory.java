package com.arcadiaeditions.payments.processing.infrastructure.jpa.inmemory;

import com.arcadiaeditions.payments.processing.core.domain.*;
import com.arcadiaeditions.payments.processing.core.outbound.jpa.PaymentRepository;
import java.math.*;
import java.time.*;
import java.util.*;

public class PaymentRepositoryInMemory extends InMemoryJpaRepository<Payment> implements PaymentRepository {

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return Optional.ofNullable(findByUniqueField("orderId", orderId));
    }
}

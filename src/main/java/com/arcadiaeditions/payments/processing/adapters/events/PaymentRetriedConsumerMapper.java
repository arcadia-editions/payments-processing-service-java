package com.arcadiaeditions.payments.processing.adapters.events;

import com.arcadiaeditions.payments.processing.core.inbound.dtos.ReauthorizePaymentInput;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * payment-retried-event-v1 -> ReauthorizePaymentInput. Event fields: orderId, attemptCount.
 * PaymentRetried.customerId and retriedAt have no target. Avro strings are CharSequence.
 */
@Mapper
public interface PaymentRetriedConsumerMapper {

    PaymentRetriedConsumerMapper INSTANCE = Mappers.getMapper(PaymentRetriedConsumerMapper.class);

    ReauthorizePaymentInput asReauthorizePaymentInput(
            com.arcadiaeditions.payments.processing.events.avro.PaymentRetried event);

    default String map(CharSequence value) {
        return value != null ? value.toString() : null;
    }
}

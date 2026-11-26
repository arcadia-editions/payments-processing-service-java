package com.arcadiaeditions.payments.processing.adapters.events;

import com.arcadiaeditions.payments.processing.core.inbound.dtos.RetryPaymentInput;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * payment-failed-event-v1 -> RetryPaymentInput. Both messages: orderId, attemptCount.
 * PaymentFailed.customerId, reason and failedAt have no target. Avro strings are CharSequence.
 */
@Mapper
public interface PaymentFailedConsumerMapper {

    PaymentFailedConsumerMapper INSTANCE = Mappers.getMapper(PaymentFailedConsumerMapper.class);

    RetryPaymentInput asRetryPaymentInput(com.arcadiaeditions.payments.processing.events.avro.PaymentFailed event);

    RetryPaymentInput asRetryPaymentInput(com.arcadiaeditions.payments.processing.events.avro.RetryPaymentInput event);

    default String map(CharSequence value) {
        return value != null ? value.toString() : null;
    }
}

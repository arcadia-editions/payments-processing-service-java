package com.arcadiaeditions.payments.processing.adapters.events;

import com.arcadiaeditions.payments.processing.core.inbound.dtos.VoidPaymentInput;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * payment-capture-failed-event-v1 -> VoidPaymentInput. Event fields: orderId, reason.
 * PaymentCaptureFailed.customerId and failedAt have no target. Avro strings are CharSequence.
 */
@Mapper
public interface PaymentCaptureFailedConsumerMapper {

    PaymentCaptureFailedConsumerMapper INSTANCE = Mappers.getMapper(PaymentCaptureFailedConsumerMapper.class);

    VoidPaymentInput asVoidPaymentInput(com.arcadiaeditions.payments.processing.events.avro.PaymentCaptureFailed event);

    default String map(CharSequence value) {
        return value != null ? value.toString() : null;
    }
}

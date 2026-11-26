package com.arcadiaeditions.payments.processing.core.application.mappers;

import com.arcadiaeditions.payments.processing.core.domain.*;
import com.arcadiaeditions.payments.processing.core.inbound.dtos.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(uses = {BaseMapper.class})
public interface EventsMapper {

    EventsMapper INSTANCE = Mappers.getMapper(EventsMapper.class);

    @Mapping(target = "reason", source = "failureReason")
    com.arcadiaeditions.payments.processing.events.avro.PaymentDeclined asPaymentDeclined(Payment payment);

    @Mapping(target = "reason", source = "payment.failureReason")
    com.arcadiaeditions.payments.processing.events.avro.PaymentRetryExhausted asPaymentRetryExhausted(
            Payment payment, java.time.Instant exhaustedAt); // exhaustedAt: not a Payment field, from the Clock

    com.arcadiaeditions.payments.processing.events.avro.PaymentCaptured asPaymentCaptured(Payment payment);

    com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized asPaymentAuthorized(Payment payment);

    com.arcadiaeditions.payments.processing.events.avro.PaymentRetried asPaymentRetried(
            Payment payment, java.time.Instant retriedAt); // retriedAt: not a Payment field, from the Clock

    com.arcadiaeditions.payments.processing.events.avro.PaymentCaptureFailed asPaymentCaptureFailed(Payment payment);

    com.arcadiaeditions.payments.processing.events.avro.PaymentVoided asPaymentVoided(Payment payment);

    com.arcadiaeditions.payments.processing.events.avro.PaymentVoidFailed asPaymentVoidFailed(Payment payment);

    @Mapping(target = "reason", source = "failureReason")
    com.arcadiaeditions.payments.processing.events.avro.PaymentFailed asPaymentFailed(Payment payment);
}

package com.arcadiaeditions.payments.processing.core.application.mappers;

import com.arcadiaeditions.payments.processing.core.domain.*;
import com.arcadiaeditions.payments.processing.core.inbound.dtos.*;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(uses = {BaseMapper.class})
public interface EventsMapper {

    EventsMapper INSTANCE = Mappers.getMapper(EventsMapper.class);

    com.arcadiaeditions.payments.processing.events.avro.PaymentDeclined asPaymentDeclined(Payment payment);

    com.arcadiaeditions.payments.processing.events.avro.PaymentRetryExhausted asPaymentRetryExhausted(Payment payment);

    com.arcadiaeditions.payments.processing.events.avro.PaymentCaptured asPaymentCaptured(Payment payment);

    com.arcadiaeditions.payments.processing.events.avro.PaymentAuthorized asPaymentAuthorized(Payment payment);

    com.arcadiaeditions.payments.processing.events.avro.PaymentRetried asPaymentRetried(Payment payment);

    com.arcadiaeditions.payments.processing.events.avro.PaymentCaptureFailed asPaymentCaptureFailed(Payment payment);

    com.arcadiaeditions.payments.processing.events.avro.PaymentVoided asPaymentVoided(Payment payment);

    com.arcadiaeditions.payments.processing.events.avro.PaymentFailed asPaymentFailed(Payment payment);
}

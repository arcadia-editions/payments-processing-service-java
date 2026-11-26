package com.arcadiaeditions.payments.processing.adapters.events;

import com.arcadiaeditions.fulfillment.shipping.events.avro.FulfillmentScheduled;
import com.arcadiaeditions.payments.processing.core.inbound.dtos.CapturePaymentInput;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * FulfillmentScheduled -> CapturePaymentInput. Only orderId is mapped: the other event fields have no target.
 * Avro strings are CharSequence.
 */
@Mapper
public interface FulfillmentScheduledConsumerMapper {

    FulfillmentScheduledConsumerMapper INSTANCE = Mappers.getMapper(FulfillmentScheduledConsumerMapper.class);

    CapturePaymentInput asCapturePaymentInput(FulfillmentScheduled event);

    default String map(CharSequence value) {
        return value != null ? value.toString() : null;
    }
}

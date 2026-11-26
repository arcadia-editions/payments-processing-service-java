package com.arcadiaeditions.payments.processing.adapters.events;

import com.arcadiaeditions.orders.checkout.events.avro.OrderCreated;
import com.arcadiaeditions.payments.processing.core.inbound.dtos.AuthorizePaymentInput;
import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * OrderCreated -> AuthorizePaymentInput. Avro strings are CharSequence; amount is a double.
 * OrderCreated.version has no target field.
 */
@Mapper
public interface OrderCreatedConsumerMapper {

    OrderCreatedConsumerMapper INSTANCE = Mappers.getMapper(OrderCreatedConsumerMapper.class);

    AuthorizePaymentInput asAuthorizePaymentInput(OrderCreated event);

    default String map(CharSequence value) {
        return value != null ? value.toString() : null;
    }

    default BigDecimal map(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }
}

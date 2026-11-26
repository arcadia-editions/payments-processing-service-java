package com.arcadiaeditions.payments.processing.adapters.events;

import com.arcadiaeditions.orders.checkout.events.avro.OrderCreated;
import com.arcadiaeditions.payments.processing.asyncapi.client.IOrderCreatedEventV1ConsumerService;
import com.arcadiaeditions.payments.processing.core.inbound.PaymentsProcessingService;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: order-created-event-v1 -> PaymentsProcessingService.authorizePayment.
 * The generator does not emit adapters for AsyncAPIGenerator interfaces.
 */
@Component
public class OrderCreatedConsumerService implements IOrderCreatedEventV1ConsumerService {

    private final PaymentsProcessingService paymentsProcessingService;

    private final OrderCreatedConsumerMapper mapper = OrderCreatedConsumerMapper.INSTANCE;

    public OrderCreatedConsumerService(PaymentsProcessingService paymentsProcessingService) {
        this.paymentsProcessingService = paymentsProcessingService;
    }

    @Override
    public void onOrderCreatedEventV1(OrderCreated payload, OrderCreatedHeaders headers) {
        paymentsProcessingService.authorizePayment(mapper.asAuthorizePaymentInput(payload));
    }
}

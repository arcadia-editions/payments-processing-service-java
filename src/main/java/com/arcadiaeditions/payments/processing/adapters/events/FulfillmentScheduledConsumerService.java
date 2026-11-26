package com.arcadiaeditions.payments.processing.adapters.events;

import com.arcadiaeditions.fulfillment.shipping.events.avro.FulfillmentScheduled;
import com.arcadiaeditions.payments.processing.asyncapi.client.IFulfillmentScheduledEventV1ConsumerService;
import com.arcadiaeditions.payments.processing.core.inbound.PaymentsProcessingService;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: fulfillment-scheduled-event-v1 -> PaymentsProcessingService.capturePayment.
 * The generator does not emit adapters for AsyncAPIGenerator interfaces.
 */
@Component
public class FulfillmentScheduledConsumerService implements IFulfillmentScheduledEventV1ConsumerService {

    private final PaymentsProcessingService paymentsProcessingService;

    private final FulfillmentScheduledConsumerMapper mapper = FulfillmentScheduledConsumerMapper.INSTANCE;

    public FulfillmentScheduledConsumerService(PaymentsProcessingService paymentsProcessingService) {
        this.paymentsProcessingService = paymentsProcessingService;
    }

    @Override
    public void onFulfillmentScheduledEventV1(FulfillmentScheduled payload, FulfillmentScheduledHeaders headers) {
        paymentsProcessingService.capturePayment(mapper.asCapturePaymentInput(payload));
    }
}

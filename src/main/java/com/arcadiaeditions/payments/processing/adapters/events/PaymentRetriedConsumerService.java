package com.arcadiaeditions.payments.processing.adapters.events;

import com.arcadiaeditions.payments.processing.asyncapi.provider.IPaymentRetriedEventV1ConsumerService;
import com.arcadiaeditions.payments.processing.core.inbound.PaymentsProcessingService;
import com.arcadiaeditions.payments.processing.events.avro.PaymentRetried;
import com.arcadiaeditions.payments.processing.events.avro.ReauthorizePaymentInput;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: payment-retried-event-v1 -> PaymentsProcessingService.reauthorizePayment.
 * Stub so the Spring context boots; the generator does not emit adapters for AsyncAPIGenerator interfaces.
 */
@Component
public class PaymentRetriedConsumerService implements IPaymentRetriedEventV1ConsumerService {

    private final PaymentsProcessingService paymentsProcessingService;

    public PaymentRetriedConsumerService(PaymentsProcessingService paymentsProcessingService) {
        this.paymentsProcessingService = paymentsProcessingService;
    }

    @Override
    public void doReauthorizePayment(ReauthorizePaymentInput payload, ReauthorizePaymentInputHeaders headers) {
        // TODO CUSTOM_REQUIRED: map payload to ReauthorizePaymentInput and call
        // paymentsProcessingService.reauthorizePayment
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void doReauthorizePayment(PaymentRetried payload, PaymentRetriedHeaders headers) {
        // TODO CUSTOM_REQUIRED: map payload to ReauthorizePaymentInput and call
        // paymentsProcessingService.reauthorizePayment
        throw new UnsupportedOperationException("Not implemented");
    }
}

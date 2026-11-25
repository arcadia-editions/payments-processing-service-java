package com.arcadiaeditions.payments.processing.adapters.events;

import com.arcadiaeditions.payments.processing.asyncapi.provider.IPaymentFailedEventV1ConsumerService;
import com.arcadiaeditions.payments.processing.core.inbound.PaymentsProcessingService;
import com.arcadiaeditions.payments.processing.events.avro.PaymentFailed;
import com.arcadiaeditions.payments.processing.events.avro.RetryPaymentInput;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: payment-failed-event-v1 -> PaymentsProcessingService.retryPayment.
 * Stub so the Spring context boots; the generator does not emit adapters for AsyncAPIGenerator interfaces.
 */
@Component
public class PaymentFailedConsumerService implements IPaymentFailedEventV1ConsumerService {

    private final PaymentsProcessingService paymentsProcessingService;

    public PaymentFailedConsumerService(PaymentsProcessingService paymentsProcessingService) {
        this.paymentsProcessingService = paymentsProcessingService;
    }

    @Override
    public void doRetryPayment(RetryPaymentInput payload, RetryPaymentInputHeaders headers) {
        // TODO CUSTOM_REQUIRED: map payload to RetryPaymentInput and call paymentsProcessingService.retryPayment
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void doRetryPayment(PaymentFailed payload, PaymentFailedHeaders headers) {
        // TODO CUSTOM_REQUIRED: map payload to RetryPaymentInput and call paymentsProcessingService.retryPayment
        throw new UnsupportedOperationException("Not implemented");
    }
}

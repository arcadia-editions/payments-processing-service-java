package com.arcadiaeditions.payments.processing.adapters.events;

import com.arcadiaeditions.payments.processing.asyncapi.provider.IPaymentFailedEventV1ConsumerService;
import com.arcadiaeditions.payments.processing.core.inbound.PaymentsProcessingService;
import com.arcadiaeditions.payments.processing.events.avro.PaymentFailed;
import com.arcadiaeditions.payments.processing.events.avro.RetryPaymentInput;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: payment-failed-event-v1 -> PaymentsProcessingService.retryPayment.
 * The generator does not emit adapters for AsyncAPIGenerator interfaces.
 */
@Component
public class PaymentFailedConsumerService implements IPaymentFailedEventV1ConsumerService {

    private final PaymentsProcessingService paymentsProcessingService;

    private final PaymentFailedConsumerMapper mapper = PaymentFailedConsumerMapper.INSTANCE;

    public PaymentFailedConsumerService(PaymentsProcessingService paymentsProcessingService) {
        this.paymentsProcessingService = paymentsProcessingService;
    }

    @Override
    public void doRetryPayment(RetryPaymentInput payload, RetryPaymentInputHeaders headers) {
        paymentsProcessingService.retryPayment(mapper.asRetryPaymentInput(payload));
    }

    @Override
    public void doRetryPayment(PaymentFailed payload, PaymentFailedHeaders headers) {
        paymentsProcessingService.retryPayment(mapper.asRetryPaymentInput(payload));
    }
}

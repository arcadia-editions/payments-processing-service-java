package com.arcadiaeditions.payments.processing.adapters.events;

import com.arcadiaeditions.payments.processing.asyncapi.provider.IPaymentRetriedEventV1ConsumerService;
import com.arcadiaeditions.payments.processing.core.inbound.PaymentsProcessingService;
import com.arcadiaeditions.payments.processing.events.avro.PaymentRetried;
import com.arcadiaeditions.payments.processing.events.avro.ReauthorizePaymentInput;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: payment-retried-event-v1 -> PaymentsProcessingService.reauthorizePayment.
 * The generator does not emit adapters for AsyncAPIGenerator interfaces.
 */
@Component
public class PaymentRetriedConsumerService implements IPaymentRetriedEventV1ConsumerService {

    private final PaymentsProcessingService paymentsProcessingService;

    private final PaymentRetriedConsumerMapper mapper = PaymentRetriedConsumerMapper.INSTANCE;

    public PaymentRetriedConsumerService(PaymentsProcessingService paymentsProcessingService) {
        this.paymentsProcessingService = paymentsProcessingService;
    }

    @Override
    public void doReauthorizePayment(ReauthorizePaymentInput payload, ReauthorizePaymentInputHeaders headers) {
        paymentsProcessingService.reauthorizePayment(mapper.asReauthorizePaymentInput(payload));
    }

    @Override
    public void doReauthorizePayment(PaymentRetried payload, PaymentRetriedHeaders headers) {
        paymentsProcessingService.reauthorizePayment(mapper.asReauthorizePaymentInput(payload));
    }
}

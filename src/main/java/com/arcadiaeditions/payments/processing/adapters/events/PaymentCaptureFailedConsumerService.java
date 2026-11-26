package com.arcadiaeditions.payments.processing.adapters.events;

import com.arcadiaeditions.payments.processing.asyncapi.provider.IPaymentCaptureFailedEventV1ConsumerService;
import com.arcadiaeditions.payments.processing.core.inbound.PaymentsProcessingService;
import com.arcadiaeditions.payments.processing.events.avro.PaymentCaptureFailed;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: payment-capture-failed-event-v1 -> PaymentsProcessingService.voidPayment.
 * The generator does not emit adapters for AsyncAPIGenerator interfaces.
 */
@Component
public class PaymentCaptureFailedConsumerService implements IPaymentCaptureFailedEventV1ConsumerService {

    private final PaymentsProcessingService paymentsProcessingService;

    private final PaymentCaptureFailedConsumerMapper mapper = PaymentCaptureFailedConsumerMapper.INSTANCE;

    public PaymentCaptureFailedConsumerService(PaymentsProcessingService paymentsProcessingService) {
        this.paymentsProcessingService = paymentsProcessingService;
    }

    @Override
    public void doPaymentCaptureFailed(PaymentCaptureFailed payload, PaymentCaptureFailedHeaders headers) {
        paymentsProcessingService.voidPayment(mapper.asVoidPaymentInput(payload));
    }
}

package com.arcadiaeditions.payments.processing.adapters.events;

import com.arcadiaeditions.payments.processing.asyncapi.provider.IPaymentCaptureFailedEventV1ConsumerService;
import com.arcadiaeditions.payments.processing.core.inbound.PaymentsProcessingService;
import com.arcadiaeditions.payments.processing.events.avro.PaymentCaptureFailed;
import com.arcadiaeditions.payments.processing.events.avro.VoidPaymentInput;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: payment-capture-failed-event-v1 -> PaymentsProcessingService.voidPayment.
 * Stub so the Spring context boots; the generator does not emit adapters for AsyncAPIGenerator interfaces.
 */
@Component
public class PaymentCaptureFailedConsumerService implements IPaymentCaptureFailedEventV1ConsumerService {

    private final PaymentsProcessingService paymentsProcessingService;

    public PaymentCaptureFailedConsumerService(PaymentsProcessingService paymentsProcessingService) {
        this.paymentsProcessingService = paymentsProcessingService;
    }

    @Override
    public void doVoidPayment(VoidPaymentInput payload, VoidPaymentInputHeaders headers) {
        // TODO CUSTOM_REQUIRED: map payload to VoidPaymentInput and call paymentsProcessingService.voidPayment
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void doVoidPayment(PaymentCaptureFailed payload, PaymentCaptureFailedHeaders headers) {
        // TODO CUSTOM_REQUIRED: map payload to VoidPaymentInput and call paymentsProcessingService.voidPayment
        throw new UnsupportedOperationException("Not implemented");
    }
}

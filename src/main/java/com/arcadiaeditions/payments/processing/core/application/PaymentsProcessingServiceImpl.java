package com.arcadiaeditions.payments.processing.core.application;

import com.arcadiaeditions.payments.processing.asyncapi.provider.*;
import com.arcadiaeditions.payments.processing.core.application.mappers.*;
import com.arcadiaeditions.payments.processing.core.domain.*;
import com.arcadiaeditions.payments.processing.core.inbound.*;
import com.arcadiaeditions.payments.processing.core.inbound.dtos.*;
import com.arcadiaeditions.payments.processing.core.outbound.jpa.*;
// import com.arcadiaeditions.payments.processing.events.avro.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing [Payment].
 */
@Service
@Transactional
@org.jspecify.annotations.NullMarked
public class PaymentsProcessingServiceImpl implements PaymentsProcessingService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final PaymentsProcessingServiceMapper paymentsProcessingServiceMapper =
            PaymentsProcessingServiceMapper.INSTANCE;

    private final PaymentRepository paymentRepository;

    private final EventsMapper eventsMapper = EventsMapper.INSTANCE;
    private final PaymentsProcessingEventsProducer eventsProducer;

    /**
     * Constructor.
     */
    public PaymentsProcessingServiceImpl(
            PaymentRepository paymentRepository, PaymentsProcessingEventsProducer eventsProducer) {
        this.paymentRepository = paymentRepository;
        this.eventsProducer = eventsProducer;
    }

    public Payment authorizePayment(AuthorizePaymentInput input) {

        log.debug("Request authorizePayment: {}", input);

        var payment = paymentsProcessingServiceMapper.update(new Payment(), input);
        // TODO: implement this method
        // emit events
        var paymentAuthorized = eventsMapper.asPaymentAuthorized(payment);
        eventsProducer.onPaymentAuthorized(paymentAuthorized);
        var paymentDeclined = eventsMapper.asPaymentDeclined(payment);
        eventsProducer.onPaymentDeclined(paymentDeclined);
        var paymentFailed = eventsMapper.asPaymentFailed(payment);
        eventsProducer.onPaymentFailed(paymentFailed);
        return payment;
    }

    public Payment retryPayment(RetryPaymentInput input) {

        log.debug("Request retryPayment: {}", input);

        // TODO CUSTOM_REQUIRED: load the Payment this transition applies to, e.g. by a correlation key in the input
        Optional<Payment> foundPayment = Optional.empty();
        var payment = foundPayment
                .map(existingPayment -> {
                    PaymentTransitions.ensureCanRetryPayment(existingPayment);
                    paymentsProcessingServiceMapper.update(existingPayment, input);
                    // TODO CUSTOM_REQUIRED: call existingPayment.setStatus(...) with one of FAILED, RETRY_EXHAUSTED
                    return existingPayment;
                })
                .map(paymentRepository::save)
                .orElseThrow();
        // emit events
        var paymentRetried = eventsMapper.asPaymentRetried(payment);
        eventsProducer.onPaymentRetried(paymentRetried);
        var paymentRetryExhausted = eventsMapper.asPaymentRetryExhausted(payment);
        eventsProducer.onPaymentRetryExhausted(paymentRetryExhausted);
        return payment;
    }

    public Payment capturePayment(CapturePaymentInput input) {

        log.debug("Request capturePayment: {}", input);

        // TODO CUSTOM_REQUIRED: load the Payment this transition applies to, e.g. by a correlation key in the input
        Optional<Payment> foundPayment = Optional.empty();
        var payment = foundPayment
                .map(existingPayment -> {
                    PaymentTransitions.ensureCanCapturePayment(existingPayment);
                    paymentsProcessingServiceMapper.update(existingPayment, input);
                    existingPayment.setStatus(PaymentStatus.CAPTURED);
                    return existingPayment;
                })
                .map(paymentRepository::save)
                .orElseThrow();
        // emit events
        var paymentCaptured = eventsMapper.asPaymentCaptured(payment);
        eventsProducer.onPaymentCaptured(paymentCaptured);
        var paymentCaptureFailed = eventsMapper.asPaymentCaptureFailed(payment);
        eventsProducer.onPaymentCaptureFailed(paymentCaptureFailed);
        return payment;
    }

    public Payment voidPayment(VoidPaymentInput input) {

        log.debug("Request voidPayment: {}", input);

        // TODO CUSTOM_REQUIRED: load the Payment this transition applies to, e.g. by a correlation key in the input
        Optional<Payment> foundPayment = Optional.empty();
        var payment = foundPayment
                .map(existingPayment -> {
                    PaymentTransitions.ensureCanVoidPayment(existingPayment);
                    paymentsProcessingServiceMapper.update(existingPayment, input);
                    existingPayment.setStatus(PaymentStatus.VOIDED);
                    return existingPayment;
                })
                .map(paymentRepository::save)
                .orElseThrow();
        // emit events
        var paymentVoided = eventsMapper.asPaymentVoided(payment);
        eventsProducer.onPaymentVoided(paymentVoided);
        return payment;
    }
}

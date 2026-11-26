package com.arcadiaeditions.payments.processing.core.application;

import com.arcadiaeditions.payments.processing.asyncapi.provider.*;
import com.arcadiaeditions.payments.processing.core.application.mappers.*;
import com.arcadiaeditions.payments.processing.core.domain.*;
import com.arcadiaeditions.payments.processing.core.inbound.*;
import com.arcadiaeditions.payments.processing.core.inbound.dtos.*;
import com.arcadiaeditions.payments.processing.core.outbound.jpa.*;
import com.arcadiaeditions.payments.processing.core.outbound.processor.PaymentProcessor;
// import com.arcadiaeditions.payments.processing.events.avro.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    /** authorizedAt, declinedAt, failedAt, retriedAt, exhaustedAt, capturedAt, voidedAt. */
    private final Clock clock;

    private final PaymentProcessor paymentProcessor;

    /**
     * Constructor.
     */
    public PaymentsProcessingServiceImpl(
            PaymentRepository paymentRepository,
            PaymentsProcessingEventsProducer eventsProducer,
            Clock clock,
            PaymentProcessor paymentProcessor) {
        this.paymentRepository = paymentRepository;
        this.eventsProducer = eventsProducer;
        this.clock = clock;
        this.paymentProcessor = paymentProcessor;
    }

    Clock clock() {
        return clock;
    }

    /**
     * Creation only. Not transactional: each repository save commits on its own, so AUTHORIZATION_REQUESTED is
     * visible (getPaymentStatus) while the processor call is in flight.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Payment authorizePayment(AuthorizePaymentInput input) {

        log.debug("Request authorizePayment: {}", input);

        var existing = paymentRepository.findByOrderId(input.getOrderId());
        if (existing.isPresent()) {
            log.debug("authorizePayment redelivery for orderId {}: no-op", input.getOrderId());
            return existing.get();
        }

        var payment = paymentsProcessingServiceMapper.update(new Payment(), input);
        payment.setStatus(PaymentStatus.AUTHORIZATION_REQUESTED);
        payment.setAttemptCount(1);
        payment = paymentRepository.save(payment); // commits before calling the processor

        PaymentTransitions.ensureCanAuthorizePayment(payment);
        var result = paymentProcessor.authorize(new PaymentProcessor.AuthorizationRequest(
                payment.getOrderId(),
                payment.getPaymentMethodId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getAttemptCount()));

        switch (result.outcome()) {
            case AUTHORIZED -> {
                payment.setStatus(PaymentStatus.AUTHORIZED);
                payment.setProviderReference(result.providerReference());
                payment.setAuthorizedAt(Instant.now(clock));
                payment = paymentRepository.save(payment);
                eventsProducer.onPaymentAuthorized(eventsMapper.asPaymentAuthorized(payment));
            }
            case DECLINED -> {
                payment.setStatus(PaymentStatus.DECLINED);
                payment.setDeclinedAt(Instant.now(clock));
                payment.setFailureReason(result.reason());
                payment = paymentRepository.save(payment);
                eventsProducer.onPaymentDeclined(eventsMapper.asPaymentDeclined(payment));
            }
            // TODO T-18 (FAILED -> PaymentFailed)
            default ->
                throw new UnsupportedOperationException(
                        "authorizePayment outcome not implemented yet: " + result.outcome());
        }
        return payment;
    }

    public Optional<Payment> reauthorizePayment(ReauthorizePaymentInput input) {

        log.debug("Request reauthorizePayment: {}", input);

        // TODO CUSTOM_REQUIRED: load the Payment this transition applies to, e.g. by a correlation key in the input
        Optional<Payment> foundPayment = Optional.empty();
        var payment = foundPayment
                .map(existingPayment -> {
                    PaymentTransitions.ensureCanReauthorizePayment(existingPayment);
                    paymentsProcessingServiceMapper.update(existingPayment, input);
                    // TODO CUSTOM_REQUIRED: call existingPayment.setStatus(...) with one of AUTHORIZED, DECLINED,
                    // FAILED
                    return existingPayment;
                })
                .map(paymentRepository::save);
        if (payment.isPresent()) {
            // emit events
            var paymentAuthorized = eventsMapper.asPaymentAuthorized(payment.get());
            eventsProducer.onPaymentAuthorized(paymentAuthorized);
            var paymentDeclined = eventsMapper.asPaymentDeclined(payment.get());
            eventsProducer.onPaymentDeclined(paymentDeclined);
            var paymentFailed = eventsMapper.asPaymentFailed(payment.get());
            eventsProducer.onPaymentFailed(paymentFailed);
        }
        return payment;
    }

    public Optional<Payment> retryPayment(RetryPaymentInput input) {

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
                .map(paymentRepository::save);
        if (payment.isPresent()) {
            // emit events
            var paymentRetried = eventsMapper.asPaymentRetried(payment.get());
            eventsProducer.onPaymentRetried(paymentRetried);
            var paymentRetryExhausted = eventsMapper.asPaymentRetryExhausted(payment.get());
            eventsProducer.onPaymentRetryExhausted(paymentRetryExhausted);
        }
        return payment;
    }

    public Payment capturePayment(CapturePaymentInput input) {

        log.debug("Request capturePayment: {}", input);

        var payment = paymentRepository.findByOrderId(input.getOrderId()).orElseThrow();
        switch (payment.getStatus()) {
            case CAPTURED, CAPTURE_FAILED, VOIDED -> {
                log.debug("capturePayment redelivery for orderId {}: no-op", input.getOrderId());
                return payment;
            }
            default -> PaymentTransitions.ensureCanCapturePayment(payment);
        }
        var result = paymentProcessor.capture(new PaymentProcessor.CaptureRequest(
                payment.getOrderId(),
                payment.getPaymentMethodId(),
                payment.getProviderReference(),
                payment.getAmount(),
                payment.getCurrency()));
        if (!result.captured()) {
            // TODO T-23 (CAPTURE_FAILED -> PaymentCaptureFailed)
            throw new UnsupportedOperationException("capturePayment capture failure not implemented yet");
        }
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAt(Instant.now(clock));
        payment = paymentRepository.save(payment);
        eventsProducer.onPaymentCaptured(eventsMapper.asPaymentCaptured(payment));
        return payment;
    }

    public Optional<Payment> voidPayment(VoidPaymentInput input) {

        log.debug("Request voidPayment: {}", input);

        // TODO CUSTOM_REQUIRED: load the Payment this transition applies to, e.g. by a correlation key in the input
        Optional<Payment> foundPayment = Optional.empty();
        var payment = foundPayment
                .map(existingPayment -> {
                    PaymentTransitions.ensureCanVoidPayment(existingPayment);
                    paymentsProcessingServiceMapper.update(existingPayment, input);
                    // TODO CUSTOM_REQUIRED: call existingPayment.setStatus(...) with one of VOIDED, VOID_FAILED
                    return existingPayment;
                })
                .map(paymentRepository::save);
        if (payment.isPresent()) {
            // emit events
            var paymentVoided = eventsMapper.asPaymentVoided(payment.get());
            eventsProducer.onPaymentVoided(paymentVoided);
            var paymentVoidFailed = eventsMapper.asPaymentVoidFailed(payment.get());
            eventsProducer.onPaymentVoidFailed(paymentVoidFailed);
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public GetPaymentStatusResult getPaymentStatus(GetPaymentStatusInput input) {

        log.debug("Request getPaymentStatus: {}", input);

        return paymentRepository
                .findByOrderId(input.getOrderId())
                .map(payment -> new GetPaymentStatusResult()
                        .setOutcome(GetPaymentStatusOutcome.STARTED)
                        .setStarted(new PaymentStartedResult()
                                .setOrderId(payment.getOrderId())
                                .setStatus(payment.getStatus())))
                .orElseGet(() -> new GetPaymentStatusResult()
                        .setOutcome(GetPaymentStatusOutcome.NO_PAYMENT)
                        .setNoPayment(new NoPaymentResult().setOrderId(input.getOrderId())));
    }
}

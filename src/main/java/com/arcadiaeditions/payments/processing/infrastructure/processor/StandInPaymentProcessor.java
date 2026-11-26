package com.arcadiaeditions.payments.processing.infrastructure.processor;

import com.arcadiaeditions.payments.processing.core.outbound.processor.PaymentProcessor;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for the payment processor (ADR 0002): the outcome is forced by the paymentMethodId.
 * Triggers: pm_decline, pm_fail_once, pm_fail_always, pm_capture_fail, pm_void_fail, and pm_timeout (the processor
 * does not answer within the timeout); any other value succeeds. Provider references: STANDIN-{orderId},
 * STANDIN-CAPFAIL-{orderId} for pm_capture_fail. The authorize call times out after {@code
 * app.payment-processor.timeout} (30 s).
 */
@Component
public class StandInPaymentProcessor implements PaymentProcessor {

    public static final String PM_DECLINE = "pm_decline";
    public static final String PM_FAIL_ONCE = "pm_fail_once";
    public static final String PM_FAIL_ALWAYS = "pm_fail_always";
    public static final String PM_CAPTURE_FAIL = "pm_capture_fail";
    public static final String PM_VOID_FAIL = "pm_void_fail";
    public static final String PM_TIMEOUT = "pm_timeout";

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private static final String CAPFAIL_PREFIX = "STANDIN-CAPFAIL-";

    private final Duration timeout;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public StandInPaymentProcessor(@Value("${app.payment-processor.timeout:30s}") Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public AuthorizationResult authorize(AuthorizationRequest request) {
        var call = executor.submit(() -> processorAuthorize(request));
        try {
            return call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            return AuthorizationResult.failed(PROCESSOR_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AuthorizationResult.failed(PROCESSOR_UNAVAILABLE);
        } catch (ExecutionException e) {
            return AuthorizationResult.failed(PROCESSOR_UNAVAILABLE);
        }
    }

    private AuthorizationResult processorAuthorize(AuthorizationRequest request) throws InterruptedException {
        String paymentMethodId = request.paymentMethodId();
        if (PM_TIMEOUT.equals(paymentMethodId)) {
            Thread.sleep(timeout.plusSeconds(1)); // answers too late
        }
        if (PM_DECLINE.equals(paymentMethodId)) {
            return AuthorizationResult.declined(CARD_DECLINED);
        }
        if (PM_FAIL_ALWAYS.equals(paymentMethodId)
                || (PM_FAIL_ONCE.equals(paymentMethodId) && request.attemptCount() <= 1)) {
            return AuthorizationResult.failed(PROCESSOR_UNAVAILABLE);
        }
        String reference = PM_CAPTURE_FAIL.equals(paymentMethodId)
                ? CAPFAIL_PREFIX + request.orderId()
                : "STANDIN-" + request.orderId();
        return AuthorizationResult.authorized(reference);
    }

    @Override
    public CaptureResult capture(CaptureRequest request) {
        if (request.providerReference() != null && request.providerReference().startsWith(CAPFAIL_PREFIX)) {
            return new CaptureResult(false, CAPTURE_REJECTED);
        }
        return new CaptureResult(true, null);
    }

    @Override
    public VoidResult voidAuthorization(VoidRequest request) {
        if (PM_VOID_FAIL.equals(request.paymentMethodId())) {
            return new VoidResult(false, VOID_REJECTED);
        }
        return new VoidResult(true, null);
    }
}

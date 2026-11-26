package com.arcadiaeditions.payments.processing.core.outbound.processor;

import java.math.BigDecimal;

/**
 * Payment processor port (ADR 0002): authorization hold, later capture or void of that hold. The processor call
 * times out after 30 seconds and becomes FAILED with {@link #PROCESSOR_TIMEOUT}.
 */
public interface PaymentProcessor {

    /** Failure reasons (Payment.failureReason). */
    String CARD_DECLINED = "card_declined";

    String PROCESSOR_UNAVAILABLE = "processor_unavailable";
    String PROCESSOR_TIMEOUT = "processor_timeout";
    String CAPTURE_REJECTED = "capture_rejected";
    String VOID_REJECTED = "void_rejected";

    AuthorizationResult authorize(AuthorizationRequest request);

    CaptureResult capture(CaptureRequest request);

    VoidResult voidAuthorization(VoidRequest request);

    /** {@code attemptCount}: 1 for authorizePayment, Payment.attemptCount for reauthorizePayment. */
    record AuthorizationRequest(
            String orderId, String paymentMethodId, BigDecimal amount, String currency, int attemptCount) {}

    enum AuthorizationOutcome {
        AUTHORIZED,
        DECLINED,
        FAILED
    }

    /** {@code providerReference} when AUTHORIZED, {@code reason} when DECLINED or FAILED. */
    record AuthorizationResult(AuthorizationOutcome outcome, String providerReference, String reason) {

        public static AuthorizationResult authorized(String providerReference) {
            return new AuthorizationResult(AuthorizationOutcome.AUTHORIZED, providerReference, null);
        }

        public static AuthorizationResult declined(String reason) {
            return new AuthorizationResult(AuthorizationOutcome.DECLINED, null, reason);
        }

        public static AuthorizationResult failed(String reason) {
            return new AuthorizationResult(AuthorizationOutcome.FAILED, null, reason);
        }
    }

    record CaptureRequest(
            String orderId, String paymentMethodId, String providerReference, BigDecimal amount, String currency) {}

    /** {@code captured} false carries the {@code reason}. */
    record CaptureResult(boolean captured, String reason) {}

    record VoidRequest(String orderId, String paymentMethodId, String providerReference, String reason) {}

    /** {@code voided} false carries the {@code reason}. */
    record VoidResult(boolean voided, String reason) {}
}

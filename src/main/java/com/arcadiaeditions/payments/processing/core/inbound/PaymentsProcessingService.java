package com.arcadiaeditions.payments.processing.core.inbound;

import com.arcadiaeditions.payments.processing.core.domain.*;
import com.arcadiaeditions.payments.processing.core.inbound.dtos.*;
import java.math.*;
import java.time.*;
import java.util.*;

/**
 * Inbound Service Port for managing [Payment].
 */
@org.jspecify.annotations.NullMarked
public interface PaymentsProcessingService {

    /**
     *
     *
     * With Events: [[PaymentAuthorized, PaymentDeclined, PaymentFailed]].
     *
     */
    public Payment authorizePayment(AuthorizePaymentInput input);
    /**
     *
     *
     * With Events: [[PaymentRetried, PaymentRetryExhausted]].
     *
     */
    public Payment retryPayment(RetryPaymentInput input);
    /**
     *
     *
     * With Events: [[PaymentCaptured, PaymentCaptureFailed]].
     *
     */
    public Payment capturePayment(CapturePaymentInput input);
    /**
     *
     *
     * With Events: [PaymentVoided].
     *
     */
    public Payment voidPayment(VoidPaymentInput input);
}

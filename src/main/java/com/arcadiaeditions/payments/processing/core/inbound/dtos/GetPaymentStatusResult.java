package com.arcadiaeditions.payments.processing.core.inbound.dtos;

import com.arcadiaeditions.payments.processing.core.domain.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.*;
import java.time.*;
import java.util.*;

/**
 * GetPaymentStatusResult.
 */
public class GetPaymentStatusResult implements Serializable {

    @NotNull
    private GetPaymentStatusOutcome outcome;

    private NoPaymentResult noPayment;

    private PaymentStartedResult started;

    public GetPaymentStatusOutcome getOutcome() {
        return outcome;
    }

    public GetPaymentStatusResult setOutcome(GetPaymentStatusOutcome outcome) {
        this.outcome = outcome;
        return this;
    }

    public NoPaymentResult getNoPayment() {
        return noPayment;
    }

    public GetPaymentStatusResult setNoPayment(NoPaymentResult noPayment) {
        this.noPayment = noPayment;
        return this;
    }

    public PaymentStartedResult getStarted() {
        return started;
    }

    public GetPaymentStatusResult setStarted(PaymentStartedResult started) {
        this.started = started;
        return this;
    }
}

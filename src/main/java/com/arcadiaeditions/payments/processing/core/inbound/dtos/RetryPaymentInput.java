package com.arcadiaeditions.payments.processing.core.inbound.dtos;

import com.arcadiaeditions.payments.processing.core.domain.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.*;
import java.time.*;
import java.util.*;

/**
 * RetryPaymentInput.
 */
public class RetryPaymentInput implements Serializable {

    @NotNull
    private String orderId;

    @NotNull
    private Integer attemptCount;

    public String getOrderId() {
        return orderId;
    }

    public RetryPaymentInput setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public RetryPaymentInput setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
        return this;
    }
}

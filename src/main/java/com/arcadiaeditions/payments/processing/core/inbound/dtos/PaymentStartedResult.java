package com.arcadiaeditions.payments.processing.core.inbound.dtos;

import com.arcadiaeditions.payments.processing.core.domain.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.*;
import java.time.*;
import java.util.*;

/**
 * PaymentStartedResult.
 */
public class PaymentStartedResult implements Serializable {

    private String orderId;

    private PaymentStatus status;

    public String getOrderId() {
        return orderId;
    }

    public PaymentStartedResult setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentStartedResult setStatus(PaymentStatus status) {
        this.status = status;
        return this;
    }
}

package com.arcadiaeditions.payments.processing.core.inbound.dtos;

import com.arcadiaeditions.payments.processing.core.domain.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.*;
import java.time.*;
import java.util.*;

/**
 * NoPaymentResult.
 */
public class NoPaymentResult implements Serializable {

    private String orderId;

    public String getOrderId() {
        return orderId;
    }

    public NoPaymentResult setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }
}

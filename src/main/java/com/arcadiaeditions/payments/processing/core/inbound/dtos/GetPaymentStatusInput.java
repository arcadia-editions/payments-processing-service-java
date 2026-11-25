package com.arcadiaeditions.payments.processing.core.inbound.dtos;

import com.arcadiaeditions.payments.processing.core.domain.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.*;
import java.time.*;
import java.util.*;

/**
 * GetPaymentStatusInput.
 */
public class GetPaymentStatusInput implements Serializable {

    @NotNull
    private String orderId;

    public String getOrderId() {
        return orderId;
    }

    public GetPaymentStatusInput setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }
}

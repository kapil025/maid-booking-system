package com.rupeek.maidbooking.service.payment;

import com.rupeek.maidbooking.domain.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Gateway adapter contract. Gateway operations must be idempotent by operation key.
 */
public interface PaymentProcessor {
    PaymentMethod method();

    String charge(UUID bookingId, BigDecimal amount, String idempotencyKey);

    void refund(UUID bookingId, BigDecimal amount, String idempotencyKey);
}

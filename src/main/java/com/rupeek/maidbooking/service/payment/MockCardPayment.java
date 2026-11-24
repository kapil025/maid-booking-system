package com.rupeek.maidbooking.service.payment;

import com.rupeek.maidbooking.domain.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class MockCardPayment implements PaymentProcessor {
    @Override
    public PaymentMethod method() {
        return PaymentMethod.CARD;
    }

    @Override
    public String charge(UUID bookingId, BigDecimal amount, String idempotencyKey) {
        // Deterministic fake transaction reference; replace with provider SDK in production.
        return "mock-card-" + bookingId;
    }

    @Override
    public void refund(UUID bookingId, BigDecimal amount, String idempotencyKey) {
        // Mock refund. A real adapter must use idempotencyKey with the gateway.
    }
}

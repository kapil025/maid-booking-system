package com.rupeek.maidbooking.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record Booking(UUID id, UUID maidId, UUID customerId, BookingType type, TimeSlot slot,
                      BigDecimal amount, BookingStatus status, PaymentStatus paymentStatus, UUID seriesId,
                      String idempotencyKey, PaymentMethod paymentMethod, String paymentReference,
                      BigDecimal refundedAmount) {
    public Booking {
        Objects.requireNonNull(id);
        Objects.requireNonNull(maidId);
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(slot);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(status);
        Objects.requireNonNull(paymentStatus);
        Objects.requireNonNull(refundedAmount);
        if (amount.signum() < 0 || refundedAmount.signum() < 0 || refundedAmount.compareTo(amount) > 0)
            throw new IllegalArgumentException("Booking amount/refund amount is invalid");
        if (status == BookingStatus.CONFIRMED && paymentStatus != PaymentStatus.PAID)
            throw new IllegalArgumentException("Confirmed booking must be paid");
        if (paymentStatus == PaymentStatus.PAID && status != BookingStatus.CONFIRMED && status != BookingStatus.CANCELLED)
            throw new IllegalArgumentException("Paid booking must be confirmed or cancelled");
        if ((paymentStatus == PaymentStatus.PAID || paymentStatus == PaymentStatus.PARTIALLY_REFUNDED || paymentStatus == PaymentStatus.REFUNDED)
                && (paymentMethod == null || paymentReference == null || paymentReference.isBlank()))
            throw new IllegalArgumentException("Successful payment/refund must retain payment method and transaction reference");
        if ((paymentStatus == PaymentStatus.REFUNDED || paymentStatus == PaymentStatus.PARTIALLY_REFUNDED) && status != BookingStatus.CANCELLED)
            throw new IllegalArgumentException("Refunded booking must be cancelled");
        if (paymentStatus == PaymentStatus.REFUNDED && refundedAmount.compareTo(amount) != 0)
            throw new IllegalArgumentException("Fully refunded payment must equal booking amount");
        if (paymentStatus == PaymentStatus.PARTIALLY_REFUNDED && (refundedAmount.signum() == 0 || refundedAmount.compareTo(amount) >= 0))
            throw new IllegalArgumentException("Partial refund must be between zero and full amount");
    }

    public Booking withPayment(PaymentStatus p, PaymentMethod method, String reference) {
        BookingStatus newStatus = p == PaymentStatus.PAID ? BookingStatus.CONFIRMED : status;
        return new Booking(id, maidId, customerId, type, slot, amount, newStatus, p, seriesId, idempotencyKey, method, reference, refundedAmount);
    }

    public Booking withStatus(BookingStatus newStatus, PaymentStatus newPaymentStatus) {
        if (status == BookingStatus.CANCELLED && newStatus != BookingStatus.CANCELLED)
            throw new IllegalStateException("Cancelled booking cannot be reopened");
        if (status == BookingStatus.CONFIRMED && newStatus == BookingStatus.CREATED)
            throw new IllegalStateException("Confirmed booking cannot return to created state");
        return new Booking(id, maidId, customerId, type, slot, amount, newStatus, newPaymentStatus, seriesId, idempotencyKey, paymentMethod, paymentReference, refundedAmount);
    }

    public Booking withRefund(BigDecimal totalRefunded, PaymentStatus newPaymentStatus) {
        return new Booking(id, maidId, customerId, type, slot, amount, BookingStatus.CANCELLED, newPaymentStatus, seriesId, idempotencyKey, paymentMethod, paymentReference, totalRefunded);
    }
}

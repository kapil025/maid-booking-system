package com.rupeek.maidbooking.service.cancellation;

import com.rupeek.maidbooking.domain.Booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

@Component
public class StandardCancellationPolicy implements CancellationPolicy {
    public BigDecimal refundAmount(Booking booking) {
        return booking.slot().start().isAfter(LocalDateTime.now()) ? booking.amount() : BigDecimal.ZERO;
    }
}

package com.rupeek.maidbooking.service.cancellation;

import com.rupeek.maidbooking.domain.Booking;

import java.math.BigDecimal;

public interface CancellationPolicy {
    BigDecimal refundAmount(Booking booking);
}

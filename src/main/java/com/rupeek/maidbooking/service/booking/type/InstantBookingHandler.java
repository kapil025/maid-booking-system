package com.rupeek.maidbooking.service.booking.type;

import com.rupeek.maidbooking.domain.BookingType;
import com.rupeek.maidbooking.domain.TimeSlot;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

@Component
public class InstantBookingHandler implements BookingTypeHandler {
    @Override
    public BookingType type() {
        return BookingType.INSTANT;
    }

    @Override
    public void validate(TimeSlot slot, LocalDateTime now) {
        if (slot.end().isBefore(now) || slot.start().isBefore(now.minusMinutes(1)) || slot.start().isAfter(now.plusMinutes(2))) {
            throw new IllegalArgumentException("Instant booking must start now (within a 2-minute tolerance)");
        }
    }
}

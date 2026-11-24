package com.rupeek.maidbooking.service.booking.type;

import com.rupeek.maidbooking.domain.BookingType;
import com.rupeek.maidbooking.domain.TimeSlot;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

@Component
public class RecurringBookingHandler implements BookingTypeHandler {
    @Override
    public BookingType type() {
        return BookingType.RECURRING;
    }

    @Override
    public void validate(TimeSlot slot, LocalDateTime now) {
        if (!slot.start().isAfter(now))
            throw new IllegalArgumentException("Recurring occurrences must be in the future");
    }
}

package com.rupeek.maidbooking.service.booking.type;

import com.rupeek.maidbooking.domain.BookingType;
import com.rupeek.maidbooking.domain.TimeSlot;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

@Component
public class ScheduledBookingHandler implements BookingTypeHandler {
    @Override
    public BookingType type() {
        return BookingType.SCHEDULED;
    }

    @Override
    public void validate(TimeSlot slot, LocalDateTime now) {
        if (!slot.start().isAfter(now)) throw new IllegalArgumentException("Scheduled booking must be in the future");
    }
}

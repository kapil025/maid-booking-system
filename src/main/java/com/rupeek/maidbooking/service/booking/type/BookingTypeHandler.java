package com.rupeek.maidbooking.service.booking.type;

import com.rupeek.maidbooking.domain.BookingType;
import com.rupeek.maidbooking.domain.TimeSlot;

import java.time.LocalDateTime;

/**
 * Encapsulates type-specific booking validation.
 */
public interface BookingTypeHandler {
    BookingType type();

    void validate(TimeSlot slot, LocalDateTime now);
}

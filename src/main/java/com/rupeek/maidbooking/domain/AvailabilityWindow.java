package com.rupeek.maidbooking.domain;

import java.time.*;
import java.util.Objects;

public record AvailabilityWindow(Weekday day, LocalTime from, LocalTime to) {
    public AvailabilityWindow {
        Objects.requireNonNull(day);
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        if (!to.isAfter(from)) throw new IllegalArgumentException("Availability end must be after start");
    }

    public boolean contains(TimeSlot slot) {
        return slot.start().getDayOfWeek().name().equals(day.name())
                && slot.end().toLocalDate().equals(slot.start().toLocalDate())
                && !slot.start().toLocalTime().isBefore(from) && !slot.end().toLocalTime().isAfter(to);
    }
}

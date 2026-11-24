package com.rupeek.maidbooking.domain;

import java.time.*;
import java.util.Objects;

public record TimeSlot(LocalDateTime start, LocalDateTime end) {
    public TimeSlot {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) throw new IllegalArgumentException("End must be after start");
        if (Duration.between(start, end).toSeconds() < 60)
            throw new IllegalArgumentException("Booking duration must be at least one minute");
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end()) && other.start().isBefore(end);
    }

    public long durationMinutes() {
        return Duration.between(start, end).toMinutes();
    }
}

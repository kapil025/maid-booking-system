package com.rupeek.maidbooking.service.maid.filter;

import com.rupeek.maidbooking.domain.*;

import java.util.List;

import com.rupeek.maidbooking.repository.BookingRepository;
import com.rupeek.maidbooking.service.maid.MaidSearchCriteria;
import org.springframework.stereotype.Component;

@Component
public class AvailabilityFilter implements MaidSearchFilter {
    private final BookingRepository bookingRepository;

    public AvailabilityFilter(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public boolean matches(Maid maid, MaidSearchCriteria c) {
        return matches(maid, c, bookingRepository.findAll());
    }

    public boolean matches(Maid maid, MaidSearchCriteria c, List<Booking> snapshot) {
        TimeSlot slot = c.requestedSlot();
        if (slot == null) return true;
        return maid.availableBySchedule(slot) && snapshot.stream().noneMatch(b ->
                b.maidId().equals(maid.id()) && b.status() != BookingStatus.CANCELLED && b.slot().overlaps(slot));
    }
}

package com.rupeek.maidbooking.service.maid.filter;

import com.rupeek.maidbooking.domain.Maid;
import com.rupeek.maidbooking.service.maid.MaidSearchCriteria;
import com.rupeek.maidbooking.domain.Booking;

import java.util.List;

/**
 * Extension seam for one independent discovery criterion.
 */
public interface MaidSearchFilter {
    boolean matches(Maid maid, MaidSearchCriteria criteria);

    default boolean matches(Maid maid, MaidSearchCriteria criteria, List<Booking> bookingSnapshot) {
        return matches(maid, criteria);
    }
}

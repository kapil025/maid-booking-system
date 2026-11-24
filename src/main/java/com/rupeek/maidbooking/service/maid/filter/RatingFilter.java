package com.rupeek.maidbooking.service.maid.filter;

import com.rupeek.maidbooking.domain.Maid;
import com.rupeek.maidbooking.service.maid.MaidSearchCriteria;
import org.springframework.stereotype.Component;

@Component
public class RatingFilter implements MaidSearchFilter {
    public boolean matches(Maid maid, MaidSearchCriteria c) {
        return c.minimumRating() == null || maid.rating() >= c.minimumRating();
    }
}

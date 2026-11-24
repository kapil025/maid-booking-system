package com.rupeek.maidbooking.service.maid.filter;

import com.rupeek.maidbooking.domain.Maid;
import com.rupeek.maidbooking.service.maid.MaidSearchCriteria;
import org.springframework.stereotype.Component;

@Component
public class GenderFilter implements MaidSearchFilter {
    public boolean matches(Maid maid, MaidSearchCriteria c) {
        return c.gender() == null || c.gender().isBlank() || c.gender().equalsIgnoreCase("ANY") || maid.gender().equalsIgnoreCase(c.gender());
    }
}

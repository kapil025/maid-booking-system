package com.rupeek.maidbooking.service.maid.filter;

import com.rupeek.maidbooking.domain.Maid;
import com.rupeek.maidbooking.service.maid.MaidSearchCriteria;
import org.springframework.stereotype.Component;

@Component
public class LocalityFilter implements MaidSearchFilter {
    public boolean matches(Maid maid, MaidSearchCriteria c) {
        return c.locality() == null || c.locality().isBlank() || maid.locality().equalsIgnoreCase(c.locality().trim());
    }
}

package com.rupeek.maidbooking.service.maid.filter;

import com.rupeek.maidbooking.domain.Maid;
import com.rupeek.maidbooking.service.maid.MaidSearchCriteria;
import org.springframework.stereotype.Component;

@Component
public class PriceFilter implements MaidSearchFilter {
    @Override
    public boolean matches(Maid maid, MaidSearchCriteria criteria) {
        return (criteria.minimumHourlyRate() == null
                || maid.hourlyRate().compareTo(criteria.minimumHourlyRate()) >= 0)
                && (criteria.maximumHourlyRate() == null
                || maid.hourlyRate().compareTo(criteria.maximumHourlyRate()) <= 0);
    }
}

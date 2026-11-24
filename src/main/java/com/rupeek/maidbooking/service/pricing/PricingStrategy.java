package com.rupeek.maidbooking.service.pricing;

import com.rupeek.maidbooking.domain.Maid;
import com.rupeek.maidbooking.domain.TimeSlot;

import java.math.BigDecimal;

public interface PricingStrategy {
    BigDecimal price(Maid maid, TimeSlot slot);
}

package com.rupeek.maidbooking.service.pricing;

import com.rupeek.maidbooking.domain.Maid;
import com.rupeek.maidbooking.domain.TimeSlot;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

@Component
public class HourlyPricingStrategy implements PricingStrategy {
    public BigDecimal price(Maid maid, TimeSlot slot) {
        return maid.hourlyRate().multiply(BigDecimal.valueOf(slot.durationMinutes())).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}

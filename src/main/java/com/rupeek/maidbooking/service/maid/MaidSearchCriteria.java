package com.rupeek.maidbooking.service.maid;

import com.rupeek.maidbooking.domain.Skill;
import com.rupeek.maidbooking.domain.TimeSlot;

import java.math.BigDecimal;

/**
 * Immutable search query. Criteria can be extended independently of search orchestration.
 */
public record MaidSearchCriteria(String locality, Skill skill, Double minimumRating,
                                 BigDecimal minimumHourlyRate, BigDecimal maximumHourlyRate,
                                 String gender, TimeSlot requestedSlot) {
    public MaidSearchCriteria {
        if (minimumRating != null && (minimumRating < 0 || minimumRating > 5 || Double.isNaN(minimumRating))) {
            throw new IllegalArgumentException("minimumRating must be between 0 and 5");
        }
        if (minimumHourlyRate != null && minimumHourlyRate.signum() < 0) {
            throw new IllegalArgumentException("minimumHourlyRate cannot be negative");
        }
        if (maximumHourlyRate != null && maximumHourlyRate.signum() < 0) {
            throw new IllegalArgumentException("maximumHourlyRate cannot be negative");
        }
        if (minimumHourlyRate != null && maximumHourlyRate != null
                && minimumHourlyRate.compareTo(maximumHourlyRate) > 0) {
            throw new IllegalArgumentException("minimumHourlyRate cannot exceed maximumHourlyRate");
        }
        if (gender != null && !gender.isBlank() && !gender.equalsIgnoreCase("ANY")
                && !gender.equalsIgnoreCase("FEMALE") && !gender.equalsIgnoreCase("MALE")
                && !gender.equalsIgnoreCase("OTHER")) {
            throw new IllegalArgumentException("gender must be ANY, FEMALE, MALE, or OTHER");
        }
    }
}

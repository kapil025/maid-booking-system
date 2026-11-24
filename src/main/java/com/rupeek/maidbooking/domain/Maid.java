package com.rupeek.maidbooking.domain;

import java.math.BigDecimal;
import java.util.*;

public record Maid(UUID id, String name, String locality, Set<Skill> skills,
                   BigDecimal hourlyRate, double rating, String gender,
                   List<AvailabilityWindow> availability) {
    public Maid {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(locality);
        if (name.isBlank() || locality.isBlank()) throw new IllegalArgumentException("Name and locality are required");
        Objects.requireNonNull(skills);
        Objects.requireNonNull(availability);
        skills = Set.copyOf(skills);
        availability = List.copyOf(availability);
        if (skills.isEmpty() || availability.isEmpty())
            throw new IllegalArgumentException("At least one skill and availability window are required");
        Objects.requireNonNull(hourlyRate);
        if (gender == null || !(gender.equalsIgnoreCase("FEMALE") || gender.equalsIgnoreCase("MALE") || gender.equalsIgnoreCase("OTHER")))
            throw new IllegalArgumentException("Gender must be FEMALE, MALE, or OTHER");
        if (hourlyRate.signum() <= 0) throw new IllegalArgumentException("Rate must be positive");
        if (rating < 0 || rating > 5 || Double.isNaN(rating)) throw new IllegalArgumentException("Rating must be 0..5");
        for (int i = 0; i < availability.size(); i++)
            for (int j = i + 1; j < availability.size(); j++) {
                AvailabilityWindow a = availability.get(i), b = availability.get(j);
                if (a.day() == b.day() && a.from().isBefore(b.to()) && b.from().isBefore(a.to()))
                    throw new IllegalArgumentException("Availability windows cannot overlap for the same weekday");
            }
    }

    public boolean availableBySchedule(TimeSlot slot) {
        return availability.stream().anyMatch(w -> w.contains(slot));
    }
}

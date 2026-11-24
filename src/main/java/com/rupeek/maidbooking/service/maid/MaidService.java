package com.rupeek.maidbooking.service.maid;

import com.rupeek.maidbooking.domain.*;
import com.rupeek.maidbooking.repository.BookingRepository;
import com.rupeek.maidbooking.repository.MaidRepository;
import com.rupeek.maidbooking.exception.NotFoundException;
import com.rupeek.maidbooking.service.maid.filter.*;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MaidService {
    private final MaidRepository repository;
    private final List<MaidSearchFilter> filters;
    private final BookingRepository bookingRepository;

    @Autowired
    public MaidService(MaidRepository repository, List<MaidSearchFilter> filters, BookingRepository bookingRepository) {
        this.repository = Objects.requireNonNull(repository);
        this.filters = List.copyOf(filters);
        this.bookingRepository = Objects.requireNonNull(bookingRepository);
    }

    // Convenient constructor for isolated unit tests.
    public MaidService(MaidRepository repository, BookingRepository bookings) {
        this.repository = Objects.requireNonNull(repository);
        this.bookingRepository = Objects.requireNonNull(bookings);
        this.filters = List.of(new LocalityFilter(), new SkillFilter(), new RatingFilter(), new PriceFilter(), new GenderFilter(), new AvailabilityFilter(bookings));
    }

    public Maid add(Maid maid) {
        return repository.save(Objects.requireNonNull(maid));
    }

    public List<Maid> search(MaidSearchCriteria criteria) {
        Objects.requireNonNull(criteria);
        List<Booking> bookingSnapshot = criteria.requestedSlot() == null || bookingRepository == null
                ? List.of() : bookingRepository.findAll();
        return repository.findAll().stream().filter(maid -> filters.stream()
                .allMatch(f -> f.matches(maid, criteria, bookingSnapshot))).toList();
    }

    public List<Maid> search(String locality, Skill skill, Double minRating, String gender, TimeSlot slot) {
        return search(new MaidSearchCriteria(locality, skill, minRating, null, null, gender, slot));
    }

    public Maid get(UUID id) {
        return repository.findById(Objects.requireNonNull(id)).orElseThrow(() -> new NotFoundException("Maid not found: " + id));
    }
}

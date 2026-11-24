package com.rupeek.maidbooking.repository;

import com.rupeek.maidbooking.domain.Booking;
import com.rupeek.maidbooking.domain.TimeSlot;
import com.rupeek.maidbooking.domain.BookingStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryBookingRepository implements BookingRepository {
    private final Map<UUID, Booking> records = new ConcurrentHashMap<>();

    @Override
    public Booking save(Booking booking) {
        records.put(booking.id(), booking);
        return booking;
    }

    @Override
    public Optional<Booking> findById(UUID id) {
        return Optional.ofNullable(records.get(id));
    }

    @Override
    public List<Booking> findAll() {
        return List.copyOf(records.values());
    }

    @Override
    public Optional<Booking> findByIdempotencyKey(String key) {
        return key == null ? Optional.empty() : records.values().stream().filter(b -> key.equals(b.idempotencyKey())).findFirst();
    }

    @Override
    public List<Booking> findOverlapping(UUID maidId, TimeSlot slot) {
        return records.values().stream().filter(b -> b.maidId().equals(maidId)
                && b.status() != BookingStatus.CANCELLED && b.slot().overlaps(slot)).toList();
    }

    @Override
    public List<Booking> findBySeriesId(UUID seriesId) {
        return records.values().stream().filter(b -> seriesId.equals(b.seriesId())).toList();
    }

    @Override
    public boolean existsByIdempotencyKey(String key) {
        return key != null && records.values().stream().anyMatch(b -> key.equals(b.idempotencyKey()));
    }
}

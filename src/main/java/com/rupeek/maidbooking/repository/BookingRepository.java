package com.rupeek.maidbooking.repository;

import com.rupeek.maidbooking.domain.Booking;
import com.rupeek.maidbooking.domain.TimeSlot;

import java.util.*;

public interface BookingRepository {
    Booking save(Booking booking);

    Optional<Booking> findById(UUID id);

    List<Booking> findAll();

    boolean existsByIdempotencyKey(String key);

    default Optional<Booking> findByIdempotencyKey(String key) {
        return key == null ? Optional.empty() : findAll().stream()
                .filter(b -> key.equals(b.idempotencyKey())).findFirst();
    }

    default List<Booking> findOverlapping(UUID maidId, TimeSlot slot) {
        return findAll().stream().filter(b -> b.maidId().equals(maidId)
                && b.status() != com.rupeek.maidbooking.domain.BookingStatus.CANCELLED
                && b.slot().overlaps(slot)).toList();
    }

    default List<Booking> findBySeriesId(UUID seriesId) {
        return findAll().stream().filter(b -> seriesId.equals(b.seriesId())).toList();
    }
}

package com.rupeek.maidbooking.api;

import com.rupeek.maidbooking.domain.*;
import com.rupeek.maidbooking.service.booking.BookingService;
import com.rupeek.maidbooking.service.maid.MaidService;
import com.rupeek.maidbooking.service.maid.MaidSearchCriteria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final MaidService maidService;
    private final BookingService bookingService;

    public ApiController(MaidService maidService, BookingService bookingService) {
        this.maidService = maidService;
        this.bookingService = bookingService;
    }

    public record AvailabilityDto(@NotNull Weekday day, @NotNull LocalTime from, @NotNull LocalTime to) {
    }

    public record MaidRequest(
            @NotBlank String name,
            @NotBlank String locality,
            @NotEmpty Set<Skill> skills,
            @NotNull @DecimalMin("0.01") BigDecimal hourlyRate,
            @NotNull @DecimalMin("0") @DecimalMax("5") Double rating,
            @NotBlank String gender,
            @NotEmpty List<@Valid AvailabilityDto> availability) {
    }

    public record BookingRequest(
            @NotNull UUID maidId,
            @NotNull UUID customerId,
            @NotNull BookingType type,
            @NotNull LocalDateTime start,
            @NotNull LocalDateTime end,
            @Size(max = 128) String idempotencyKey) {
    }

    public record PayRequest(@NotNull PaymentMethod method) {
    }

    public record RecurringRequest(
            @NotNull UUID maidId,
            @NotNull UUID customerId,
            @NotNull LocalDate firstDate,
            @NotNull LocalDate lastDate,
            @NotNull LocalTime startTime,
            @NotNull @Min(1) @Max(1440) Long durationMinutes,
            @NotEmpty Set<DayOfWeek> days,
            @Size(max = 128) String idempotencyKey) {
    }

    @PostMapping("/maids")
    public Maid addMaid(@Valid @RequestBody MaidRequest request) {
        List<AvailabilityWindow> windows = request.availability().stream()
                .map(window -> new AvailabilityWindow(window.day(), window.from(), window.to()))
                .toList();
        Maid maid = new Maid(UUID.randomUUID(), request.name(), request.locality(), request.skills(),
                request.hourlyRate(), request.rating(), request.gender(), windows);
        return maidService.add(maid);
    }

    @GetMapping("/maids")
    public List<Maid> searchMaids(
            @RequestParam(required = false) String locality,
            @RequestParam(required = false) Skill skill,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) BigDecimal minHourlyRate,
            @RequestParam(required = false) BigDecimal maxHourlyRate,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) LocalDateTime start,
            @RequestParam(required = false) LocalDateTime end) {
        if ((start == null) != (end == null)) {
            throw new IllegalArgumentException("Both start and end are required for time-based search");
        }
        TimeSlot requestedSlot = start == null ? null : new TimeSlot(start, end);
        MaidSearchCriteria criteria = new MaidSearchCriteria(locality, skill, minRating,
                minHourlyRate, maxHourlyRate, gender, requestedSlot);
        return maidService.search(criteria);
    }

    @PostMapping("/bookings")
    public ResponseEntity<Booking> createBooking(@Valid @RequestBody BookingRequest request) {
        TimeSlot slot = new TimeSlot(request.start(), request.end());
        Booking booking = bookingService.book(request.maidId(), request.customerId(), request.type(),
                slot, request.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @PostMapping("/bookings/recurring")
    public ResponseEntity<List<Booking>> createRecurringBookings(@Valid @RequestBody RecurringRequest request) {
        List<Booking> bookings = bookingService.recurring(request.maidId(), request.customerId(),
                request.firstDate(), request.lastDate(), request.startTime(),
                Duration.ofMinutes(request.durationMinutes()), request.days(), request.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(bookings);
    }

    @PostMapping("/bookings/{id}/pay")
    public Booking pay(@PathVariable UUID id, @Valid @RequestBody PayRequest request) {
        return bookingService.pay(id, request.method());
    }

    @PostMapping("/bookings/{id}/cancel")
    public Booking cancel(@PathVariable UUID id) {
        return bookingService.cancel(id);
    }

    @PostMapping("/booking-series/{seriesId}/cancel")
    public List<Booking> cancelSeries(@PathVariable UUID seriesId) {
        return bookingService.cancelSeries(seriesId);
    }

    @GetMapping("/bookings/{id}")
    public Booking getBooking(@PathVariable UUID id) {
        return bookingService.get(id);
    }
}

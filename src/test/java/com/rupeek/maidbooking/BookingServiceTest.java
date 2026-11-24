package com.rupeek.maidbooking;

import com.rupeek.maidbooking.domain.*;
import com.rupeek.maidbooking.repository.*;
import com.rupeek.maidbooking.service.booking.BookingService;
import com.rupeek.maidbooking.service.booking.type.*;
import com.rupeek.maidbooking.service.maid.MaidService;
import com.rupeek.maidbooking.service.payment.*;
import com.rupeek.maidbooking.service.cancellation.*;
import com.rupeek.maidbooking.service.pricing.*;
import com.rupeek.maidbooking.exception.ConflictException;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BookingServiceTest {
    static class MaidRepo implements MaidRepository {
        Maid m;

        public Maid save(Maid x) {
            m = x;
            return x;
        }

        public Optional<Maid> findById(UUID id) {
            return Optional.ofNullable(m);
        }

        public List<Maid> findAll() {
            return m == null ? List.of() : List.of(m);
        }
    }

    static class BookRepo implements BookingRepository {
        Map<UUID, Booking> map = new HashMap<>();

        public Booking save(Booking b) {
            map.put(b.id(), b);
            return b;
        }

        public Optional<Booking> findById(UUID id) {
            return Optional.ofNullable(map.get(id));
        }

        public List<Booking> findAll() {
            return List.copyOf(map.values());
        }

        public boolean existsByIdempotencyKey(String k) {
            return map.values().stream().anyMatch(b -> k != null && k.equals(b.idempotencyKey()));
        }
    }

    BookingService service;
    Maid maid;
    BookRepo br;

    @BeforeEach
    void setup() {
        MaidRepo mr = new MaidRepo();
        br = new BookRepo();
        LocalDate d = LocalDate.now().plusDays(1);
        maid = new Maid(UUID.randomUUID(), "Asha", "Indiranagar", Set.of(Skill.CLEANING), new BigDecimal("300"), 4.8, "FEMALE",
                List.of(new AvailabilityWindow(Weekday.valueOf(d.getDayOfWeek().name()), LocalTime.of(8, 0), LocalTime.of(18, 0))));
        mr.save(maid);
        MaidService ms = new MaidService(mr, br);
        service = new BookingService(br, ms, List.of(new InstantBookingHandler(), new ScheduledBookingHandler(), new RecurringBookingHandler()),
                List.of(new MockCardPayment(), new MockUpiPayment(), new MockWalletPayment()), new StandardCancellationPolicy(), new HourlyPricingStrategy());
    }

    @Test
    void bookingRejectsOverlapAndAllowsAfterCancellation() {
        LocalDate d = LocalDate.now().plusDays(1);
        TimeSlot s = new TimeSlot(d.atTime(10, 0), d.atTime(11, 0));
        Booking b = service.book(maid.id(), UUID.randomUUID(), BookingType.SCHEDULED, s, null);
        assertThrows(ConflictException.class, () -> service.book(maid.id(), UUID.randomUUID(), BookingType.SCHEDULED, s, null));
        service.cancel(b.id());
        assertDoesNotThrow(() -> service.book(maid.id(), UUID.randomUUID(), BookingType.SCHEDULED, s, null));
    }

    @Test
    void paymentIsIdempotent() {
        LocalDate d = LocalDate.now().plusDays(1);
        Booking b = service.book(maid.id(), UUID.randomUUID(), BookingType.SCHEDULED,
                new TimeSlot(d.atTime(10, 0), d.atTime(11, 0)), null);
        assertEquals(PaymentStatus.PAID, service.pay(b.id(), PaymentMethod.UPI).paymentStatus());
        assertEquals(PaymentStatus.PAID, service.pay(b.id(), PaymentMethod.UPI).paymentStatus());
    }

    @Test
    void concurrentRequestsCannotDoubleBookSameMaid() throws Exception {
        LocalDate d = LocalDate.now().plusDays(1);
        TimeSlot slot = new TimeSlot(d.atTime(12, 0), d.atTime(13, 0));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> attempt(slot));
            var second = pool.submit(() -> attempt(slot));
            int accepted = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertEquals(1, accepted, "exactly one concurrent request should reserve the slot");
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean attempt(TimeSlot slot) {
        try {
            service.book(maid.id(), UUID.randomUUID(), BookingType.SCHEDULED, slot, null);
            return true;
        } catch (ConflictException expected) {
            return false;
        }
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentPayload() {
        LocalDate d = LocalDate.now().plusDays(1);
        TimeSlot first = new TimeSlot(d.atTime(10, 0), d.atTime(11, 0));
        service.book(maid.id(), UUID.randomUUID(), BookingType.SCHEDULED, first, "same-key");
        assertThrows(ConflictException.class, () -> service.book(maid.id(), UUID.randomUUID(), BookingType.SCHEDULED,
                new TimeSlot(d.atTime(11, 0), d.atTime(12, 0)), "same-key"));
    }

    @Test
    void refundUsesSelectedPaymentMethodAndTracksAmount() {
        LocalDate d = LocalDate.now().plusDays(1);
        Booking created = service.book(maid.id(), UUID.randomUUID(), BookingType.SCHEDULED,
                new TimeSlot(d.atTime(10, 0), d.atTime(11, 0)), null);
        Booking paid = service.pay(created.id(), PaymentMethod.UPI);
        assertEquals(PaymentMethod.UPI, paid.paymentMethod());
        Booking cancelled = service.cancel(paid.id());
        assertEquals(PaymentStatus.REFUNDED, cancelled.paymentStatus());
        assertEquals(new BigDecimal("300.00"), cancelled.refundedAmount());
    }

    @Test
    void recurringRequestIsIdempotentAndSeriesCanBeCancelled() {
        LocalDate first = LocalDate.now().plusDays(1);
        Set<DayOfWeek> day = Set.of(first.getDayOfWeek());
        var series = service.recurring(maid.id(), UUID.randomUUID(), first, first.plusDays(7), LocalTime.of(10, 0),
                Duration.ofHours(1), day, "series-key");
        assertEquals(2, series.size());
        var repeated = service.recurring(maid.id(), series.get(0).customerId(), first, first.plusDays(7), LocalTime.of(10, 0),
                Duration.ofHours(1), day, "series-key");
        assertEquals(series.stream().map(Booking::id).toList(), repeated.stream().map(Booking::id).toList());
        assertEquals(2, service.cancelSeries(series.get(0).seriesId()).size());
    }

}

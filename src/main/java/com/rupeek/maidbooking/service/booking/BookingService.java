package com.rupeek.maidbooking.service.booking;

import com.rupeek.maidbooking.domain.*;
import com.rupeek.maidbooking.repository.BookingRepository;
import com.rupeek.maidbooking.service.booking.type.BookingTypeHandler;
import com.rupeek.maidbooking.service.cancellation.CancellationPolicy;
import com.rupeek.maidbooking.exception.ConflictException;
import com.rupeek.maidbooking.exception.NotFoundException;
import com.rupeek.maidbooking.service.maid.MaidService;
import com.rupeek.maidbooking.service.payment.PaymentProcessor;
import com.rupeek.maidbooking.service.pricing.PricingStrategy;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final MaidService maidService;
    private final Map<BookingType, BookingTypeHandler> handlers;
    private final Map<PaymentMethod, PaymentProcessor> paymentProcessors;
    private final CancellationPolicy cancellationPolicy;
    private final PricingStrategy pricingStrategy;
    private final Clock clock;
    // Fixed stripes avoid retaining one monitor forever for every maid/booking/idempotency key.
    private final Object[] maidLockStripes = createLockStripes(128);
    private final Object[] bookingLockStripes = createLockStripes(128);
    private final Object[] idempotencyLockStripes = createLockStripes(128);

    @Autowired
    public BookingService(BookingRepository bookingRepository, MaidService maidService,
                          List<BookingTypeHandler> typeHandlers, List<PaymentProcessor> processors,
                          CancellationPolicy cancellationPolicy, PricingStrategy pricingStrategy) {
        this(bookingRepository, maidService, typeHandlers, processors, cancellationPolicy, pricingStrategy, Clock.systemDefaultZone());
    }

    BookingService(BookingRepository bookingRepository, MaidService maidService,
                   List<BookingTypeHandler> typeHandlers, List<PaymentProcessor> processors,
                   CancellationPolicy cancellationPolicy, PricingStrategy pricingStrategy, Clock clock) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository);
        this.maidService = Objects.requireNonNull(maidService);
        this.handlers = indexUnique(typeHandlers, BookingTypeHandler::type, "booking type");
        this.paymentProcessors = indexUnique(processors, PaymentProcessor::method, "payment method");
        this.cancellationPolicy = Objects.requireNonNull(cancellationPolicy);
        this.pricingStrategy = Objects.requireNonNull(pricingStrategy);
        this.clock = Objects.requireNonNull(clock);
    }

    public Booking book(UUID maidId, UUID customerId, BookingType type, TimeSlot slot, String idempotencyKey) {
        Objects.requireNonNull(maidId, "maidId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(slot, "slot");
        validateIdempotencyKey(idempotencyKey);
        BookingTypeHandler handler = Optional.ofNullable(handlers.get(type))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported booking type: " + type));

        Object keyLock = lockForKey(idempotencyKey);
        synchronized (keyLock) {
            Booking existing = findByKey(idempotencyKey);
            if (existing != null) {
                assertSameRequest(existing, maidId, customerId, type, slot);
                return existing;
            }
            handler.validate(slot, LocalDateTime.now(clock));
            synchronized (lockForMaid(maidId)) {
                Maid maid = maidService.get(maidId);
                validateMaidAvailability(maid, slot);
                ensureNoOverlap(maidId, slot);
                Booking booking = new Booking(UUID.randomUUID(), maidId, customerId, type, slot,
                        validatedPrice(maid, slot), BookingStatus.CREATED, PaymentStatus.PENDING,
                        null, idempotencyKey, null, null, BigDecimal.ZERO);
                return bookingRepository.save(booking);
            }
        }
    }

    public Booking pay(UUID bookingId, PaymentMethod method) {
        Objects.requireNonNull(method, "payment method");
        synchronized (lockForBooking(bookingId)) {
            Booking booking = get(bookingId);
            if (booking.status() == BookingStatus.CANCELLED)
                throw new ConflictException("Cancelled booking cannot be paid");
            if (booking.paymentStatus() == PaymentStatus.PAID) return booking;
            PaymentProcessor processor = Optional.ofNullable(paymentProcessors.get(method))
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported payment method: " + method));
            // Gateway idempotency protects the charge if the process crashes between charge and save.
            String gatewayKey = "booking-charge-" + booking.id();
            String transactionReference = processor.charge(booking.id(), booking.amount(), gatewayKey);
            if (transactionReference == null || transactionReference.isBlank()) {
                throw new IllegalStateException("Payment processor did not return a transaction reference");
            }
            return bookingRepository.save(booking.withPayment(PaymentStatus.PAID, method, transactionReference));
        }
    }

    public Booking cancel(UUID bookingId) {
        synchronized (lockForBooking(bookingId)) {
            Booking booking = get(bookingId);
            if (booking.status() == BookingStatus.CANCELLED) return booking;
            BigDecimal refund = cancellationPolicy.refundAmount(booking);
            if (refund == null || refund.signum() < 0 || refund.compareTo(booking.amount()) > 0) {
                throw new IllegalStateException("Cancellation policy returned an invalid refund amount");
            }
            if ((booking.paymentStatus() == PaymentStatus.PAID || booking.paymentStatus() == PaymentStatus.PARTIALLY_REFUNDED)
                    && refund.signum() > 0) {
                PaymentProcessor processor = Optional.ofNullable(booking.paymentMethod())
                        .map(paymentProcessors::get).orElseThrow(() -> new IllegalStateException("Payment method missing; refund cannot be routed"));
                BigDecimal cumulativeRefund = booking.refundedAmount().add(refund);
                if (cumulativeRefund.compareTo(booking.amount()) > 0)
                    throw new IllegalStateException("Cancellation policy exceeds remaining refundable amount");
                processor.refund(booking.id(), refund, "booking-refund-" + booking.id() + "-" + cumulativeRefund);
                PaymentStatus refundedStatus = cumulativeRefund.compareTo(booking.amount()) == 0
                        ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
                booking = booking.withRefund(cumulativeRefund, refundedStatus);
            } else {
                booking = booking.withStatus(BookingStatus.CANCELLED, booking.paymentStatus());
            }
            return bookingRepository.save(booking);
        }
    }

    public List<Booking> cancelSeries(UUID seriesId) {
        Objects.requireNonNull(seriesId, "seriesId");
        return bookingRepository.findBySeriesId(seriesId).stream()
                .filter(b -> b.status() != BookingStatus.CANCELLED)
                .map(b -> cancel(b.id())).toList();
    }

    public List<Booking> recurring(UUID maidId, UUID customerId, LocalDate firstDate, LocalDate lastDate,
                                   LocalTime startTime, Duration duration, Set<DayOfWeek> days, String idempotencyKey) {
        Objects.requireNonNull(firstDate);
        Objects.requireNonNull(lastDate);
        Objects.requireNonNull(startTime);
        Objects.requireNonNull(duration);
        Objects.requireNonNull(days);
        if (lastDate.isBefore(firstDate) || days.isEmpty() || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Invalid recurrence range, days, or duration");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(firstDate, lastDate) > 365) {
            throw new IllegalArgumentException("Recurring booking range cannot exceed 366 calendar days");
        }
        validateIdempotencyKey(idempotencyKey);
        List<TimeSlot> occurrences = new ArrayList<>();
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            if (days.contains(date.getDayOfWeek())) {
                LocalDateTime start = date.atTime(startTime);
                occurrences.add(new TimeSlot(start, start.plus(duration)));
            }
        }
        if (occurrences.isEmpty()) throw new IllegalArgumentException("Recurrence has no matching dates");
        BookingTypeHandler handler = handlers.get(BookingType.RECURRING);
        if (handler == null) throw new IllegalArgumentException("Recurring booking is not enabled");

        synchronized (lockForKey(idempotencyKey)) {
            if (idempotencyKey != null && bookingRepository.existsByIdempotencyKey(idempotencyKey)) {
                throw new ConflictException("Idempotency key already used; use a new series key");
            }
            synchronized (lockForMaid(maidId)) {
                Maid maid = maidService.get(maidId);
                List<Booking> existingBookings = bookingRepository.findAll();
                if (idempotencyKey != null) {
                    List<Booking> priorSeries = existingBookings.stream()
                            .filter(b -> b.idempotencyKey() != null && b.idempotencyKey().startsWith(idempotencyKey + ":"))
                            .sorted(Comparator.comparing(b -> b.slot().start())).toList();
                    if (!priorSeries.isEmpty()) {
                        boolean same = priorSeries.size() == occurrences.size();
                        for (int i = 0; same && i < occurrences.size(); i++) {
                            Booking prior = priorSeries.get(i);
                            TimeSlot requested = occurrences.get(i);
                            same = prior.maidId().equals(maidId) && prior.customerId().equals(customerId)
                                    && prior.slot().equals(requested) && prior.type() == BookingType.RECURRING;
                        }
                        if (!same)
                            throw new ConflictException("Idempotency key was reused for a different recurring request");
                        return priorSeries;
                    }
                }
                LocalDateTime now = LocalDateTime.now(clock);
                for (int i = 0; i < occurrences.size(); i++) {
                    TimeSlot slot = occurrences.get(i);
                    handler.validate(slot, now);
                    validateMaidAvailability(maid, slot);
                    ensureNoOverlap(maidId, slot, existingBookings);
                    for (int j = 0; j < i; j++) {
                        if (slot.overlaps(occurrences.get(j)))
                            throw new ConflictException("Recurring occurrences overlap");
                    }
                }
                // Validation completes before the first write, avoiding partial series creation in this in-memory store.
                UUID seriesId = UUID.randomUUID();
                List<Booking> created = new ArrayList<>(occurrences.size());
                for (TimeSlot slot : occurrences) {
                    String occurrenceKey = idempotencyKey == null ? null : idempotencyKey + ":" + slot.start().toLocalDate();
                    Booking booking = new Booking(UUID.randomUUID(), maidId, customerId, BookingType.RECURRING,
                            slot, validatedPrice(maid, slot), BookingStatus.CREATED, PaymentStatus.PENDING,
                            seriesId, occurrenceKey, null, null, BigDecimal.ZERO);
                    created.add(bookingRepository.save(booking));
                }
                return List.copyOf(created);
            }
        }
    }

    public Booking get(UUID id) {
        return bookingRepository.findById(id).orElseThrow(() -> new NotFoundException("Booking not found: " + id));
    }

    private BigDecimal validatedPrice(Maid maid, TimeSlot slot) {
        BigDecimal price = pricingStrategy.price(maid, slot);
        if (price == null || price.signum() <= 0)
            throw new IllegalStateException("Pricing strategy must return a positive amount");
        return price.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void validateMaidAvailability(Maid maid, TimeSlot slot) {
        if (!maid.availableBySchedule(slot)) throw new ConflictException("Maid is outside availability window");
    }

    private void ensureNoOverlap(UUID maidId, TimeSlot slot, List<Booking> allBookings) {
        boolean conflict = allBookings.stream().anyMatch(b -> b.maidId().equals(maidId)
                && b.status() != BookingStatus.CANCELLED && b.slot().overlaps(slot));
        if (conflict) throw new ConflictException("Maid already booked for overlapping slot");
    }

    private void ensureNoOverlap(UUID maidId, TimeSlot slot) {
        if (!bookingRepository.findOverlapping(maidId, slot).isEmpty())
            throw new ConflictException("Maid already booked for overlapping slot");
    }

    private Booking findByKey(String key) {
        return bookingRepository.findByIdempotencyKey(key).orElse(null);
    }

    private void assertSameRequest(Booking booking, UUID maidId, UUID customerId, BookingType type, TimeSlot slot) {
        if (!booking.maidId().equals(maidId) || !booking.customerId().equals(customerId)
                || booking.type() != type || !booking.slot().equals(slot)) {
            throw new ConflictException("Idempotency key was already used for a different booking request");
        }
    }

    private void validateIdempotencyKey(String key) {
        if (key != null && (key.isBlank() || key.length() > 128)) {
            throw new IllegalArgumentException("Idempotency key must contain 1..128 characters");
        }
    }

    private Object lockForMaid(UUID id) {
        return maidLockStripes[Math.floorMod(id.hashCode(), maidLockStripes.length)];
    }

    private Object lockForBooking(UUID id) {
        return bookingLockStripes[Math.floorMod(id.hashCode(), bookingLockStripes.length)];
    }

    private Object lockForKey(String key) {
        return key == null ? new Object() : idempotencyLockStripes[Math.floorMod(key.hashCode(), idempotencyLockStripes.length)];
    }

    private static Object[] createLockStripes(int count) {
        Object[] locks = new Object[count];
        Arrays.setAll(locks, ignored -> new Object());
        return locks;
    }

    private static <K, V> Map<K, V> indexUnique(List<V> values, java.util.function.Function<V, K> key,
                                                String label) {
        Map<K, V> result = new HashMap<>();
        for (V value : values) {
            if (result.putIfAbsent(key.apply(value), value) != null) {
                throw new IllegalArgumentException("Duplicate " + label + " strategy: " + key.apply(value));
            }
        }
        return Map.copyOf(result);
    }
}

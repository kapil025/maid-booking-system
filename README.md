# Maid Booking System

Java 17 / Spring Boot 3.4.2 backend for maid discovery, booking, payment and cancellation.

## Build and run

Prerequisites: JDK 17+ and Maven 3.9+.

```bash
mvn clean test
mvn spring-boot:run
```

Base URL: `http://localhost:8080/api`

## REST API

- `POST /maids` — onboard a maid and weekly availability windows
- `GET /maids?locality=&skill=&minRating=&minHourlyRate=&maxHourlyRate=&gender=&start=&end=` — discovery; time-based search requires both start and end
- `POST /bookings` — create INSTANT or SCHEDULED booking
- `POST /bookings/recurring` — materialize a bounded recurrence series
- `POST /bookings/{id}/pay` — pay with CARD, UPI or WALLET
- `POST /bookings/{id}/cancel` — cancel one occurrence and apply refund policy
- `POST /booking-series/{seriesId}/cancel` — cancel remaining occurrences
- `GET /bookings/{id}` — retrieve booking

## Package design

- `domain`: immutable domain records, enums and the `TimeSlot` value object
- `api`: request/response boundary and centralized API exception mapping
- `repository`: repository contracts and in-memory implementations
- `service.maid`: maid onboarding and discovery orchestration
- `service.maid.filter`: independently extensible discovery criteria (locality, skill, rating, hourly price, gender, schedule/inventory)
- `service.booking`: booking orchestration, state and inventory coordination
- `service.booking.type`: type-specific validation for instant/scheduled/recurring
- `service.payment`: payment gateway port and mock adapters
- `service.pricing`: pricing strategy
- `service.cancellation`: cancellation/refund policy
- `service.common`: service-layer exceptions

## Correctness decisions

- Time slots use half-open intervals `[start, end)`: bookings ending at 10:00 and starting at 10:00 are adjacent, not overlapping.
- Booking overlap check and save execute under a per-maid lock in this single JVM.
- Payment and cancellation are serialized per booking. Payment method and transaction reference are stored in the booking model so refunds route through the original method.
- Gateway charge/refund operations receive stable idempotency keys. A real provider adapter must honor them.
- Recurrence is validated in full before writes and then saved as individually cancellable occurrences with a shared series ID. Series cancellation executes occurrence-by-occurrence; gateway refund failures can leave a partially cancelled series, which requires a saga/reconciliation in production.
- Booking and payment status are separate; a confirmed booking must be paid. Refund amount is tracked on the booking.
- Discovery filters implement `MaidSearchFilter`; a new filter can be registered as a Spring bean without editing the search orchestration.
- Booking type, payment, pricing and cancellation are strategy seams. Duplicate strategy registrations fail fast at startup.

## Assumptions and limitations

- Mock payment gateways always succeed and do not contact an external provider.
- Data and locks are in-memory and process-local. This is intentionally not production persistence; use DB transactions, durable idempotency records and locking/versioning for multi-instance deployment.
- Recurring schedules are materialized only for the requested date range. Payment is per occurrence.
- Availability is a weekly local-time window. Production should store the maid's time zone and use `Instant` for booking instants.
- No authentication, frontend, production deployment or real payment integration, per assignment scope.

## Next production-hardening steps

Durable transactional repository; payment outbox/saga and reconciliation; OpenAPI; audit history; metrics; pagination and database-backed search; time-zone-aware availability; integration tests against persistence and gateway contracts.

# Rupeek Maid Booking — Java 17 / Spring Boot

## Run
- Java 17+
- `mvn clean test`
- `mvn spring-boot:run`
- API base: `http://localhost:8080/api`

## Endpoints
- `POST /maids` — onboard maid with weekly availability
- `GET /maids?locality=&skill=&minRating=&gender=&start=&end=` — discovery
- `POST /bookings` — create Instant or Scheduled booking
- `POST /bookings/recurring` — create a dated series of recurring occurrences
- `POST /booking-series/{seriesId}/cancel` — cancel all remaining occurrences
- `POST /bookings/{id}/pay` — pay using CARD / UPI / WALLET
- `POST /bookings/{id}/cancel` — cancel and apply refund policy
- `GET /bookings/{id}` — booking details

Recurring booking creation is available in the service layer through `BookingService.recurring(...)`; the generated occurrences share a series ID and can be cancelled individually or using `cancelSeries(seriesId)`.

## Design
- Domain model uses immutable records and explicit enums / value objects.
- Repository interfaces isolate in-memory concurrent-map persistence.
- Booking type, payment processor, cancellation policy and pricing are strategy/extension seams.
- Booking overlap uses half-open interval semantics: `[start,end)`, so adjacent bookings are allowed.
- A per-maid lock protects availability-check + save as one critical section in this single-process implementation.
- Payment is mocked. Payment status and booking status are separate.
- Idempotency key prevents duplicate booking creation; repeated payment returns the already-paid booking.
- Availability is represented as weekly day/time windows.

## Assumptions / limitations
- No authentication, real gateway, production database, UI or deployment, matching the brief's out-of-scope list.
- LocalDateTime is treated as platform-local time; a production design should persist UTC instants plus maid/customer time zones.
- Recurring occurrences are materialized for a bounded date range. Payments are per occurrence.
- In-memory locking is process-local. For multi-instance deployment use database exclusion/locking, optimistic versioning, or distributed coordination and a durable idempotency table.
- For production, add payment outbox/saga, gateway idempotency, retry/reconciliation, audit trail, OpenAPI, metrics and integration tests.

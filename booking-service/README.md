# booking-service

> Status: **done**

**Responsibility:** bookings and the **booking saga** — the flow that goes through every other
service, with a **Resilience4j circuit breaker and fallback method** on every call, compensations,
and a recovery job.

| | |
|---|---|
| Port | 8086 |
| Data store | PostgreSQL `booking_db` |
| Design | [docs/design.md](docs/design.md) |

See the [architecture overview](../docs/architecture.md), the [saga](../docs/booking-saga.md) and
[resilience](../docs/resilience.md) docs for how this service fits in.

## Endpoints

| Method | Path | Access |
|---|---|---|
| `POST` | `/api/bookings` | authenticated — `{ eventId, sectorId, quantity, cardToken }`, optional `Idempotency-Key` |
| `GET` | `/api/bookings/{id}` | owner or `ADMIN` (includes the saga log) |
| `GET` | `/api/bookings/me` | authenticated |
| `POST` | `/api/bookings/{id}/cancel` | owner |
| `GET` | `/api/admin/bookings?status=` | `ADMIN` |
| `GET` | `/actuator/circuitbreakers`, `/actuator/circuitbreakerevents` | public |

`POST /api/bookings` answers `201` confirmed, `409` business failure (sold out, declined...), `503`
a service was unavailable — always with the booking in the body.

## Where to look in the code

| What | Where |
|---|---|
| The saga, step by step | `application/BookingSaga.java` |
| Undo steps (refund, release) and notifications | `application/Compensations.java` |
| Circuit breakers + fallback methods | `infrastructure/client/*Client.java` |
| Circuit breaker settings | `src/main/resources/application.yml` (`resilience4j.circuitbreaker`) |
| Recovery job | `application/RecoveryJob.java` |
| Booking state machine | `domain/Booking.java` |

## Run

From the repository root:

```bash
docker compose up -d --build
./scripts/demo.sh
```

> Postgres volume created before this service existed? Create its database in place:
>
> ```bash
> docker compose exec postgres psql -U postgres -c "CREATE USER booking_user WITH PASSWORD 'booking_pass'" -c "CREATE DATABASE booking_db OWNER booking_user"
> ```

## Tests

```bash
./mvnw test
```

- `BookingSagaTest` — every success, failure and fallback of the saga, against in-memory fakes
- `CircuitBreakerIntegrationTest` — real Resilience4j against WireMock: business answers don't
  count, 5xx and timeouts do, OPEN → fail fast, HALF_OPEN → CLOSED, on-behalf-of tokens
- `SagaIntegrationTest` — the whole saga over HTTP and PostgreSQL: status codes, compensations,
  the recovery job (unfinished undo, crash mid-saga, pending notification), idempotency, cancel
- `BookingControllerTest` — status per outcome, validation, ownership, admin-only routes

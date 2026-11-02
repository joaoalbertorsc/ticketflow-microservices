# booking-service

> Status: **not started**

**Responsibility:** Bookings and orchestration of the booking saga, with Resilience4j circuit breakers on every downstream call.

| | |
|---|---|
| Port | 8086 |
| Data store | PostgreSQL `booking_db` |

Internal design, data model and API contract will live in [docs/](docs/).
See the [architecture overview](../docs/architecture.md) for how this service fits in.

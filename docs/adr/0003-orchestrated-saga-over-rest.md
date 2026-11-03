# ADR 0003 — Orchestrated saga over synchronous REST

- **Status:** Accepted
- **Date:** 2026-10-17

## Context

Booking a ticket changes data in several services (seats, payment, notification). Each has its
own database, so a single transaction is impossible. Messaging is out of scope for now.

## Decision

- `booking-service` acts as the **saga orchestrator**. It calls each step in order over REST,
  stores the booking state and a step log in `booking_db`, and runs compensations when a step
  fails.
- Leaf services expose small, **idempotent** internal endpoints (reserve / confirm / release,
  charge / refund-by-booking) keyed by `bookingId`.
- Seat holds expire automatically, so a crashed orchestrator never leaves seats locked.

Orchestration was chosen over choreography because without a message broker choreography is
not practical, and orchestration keeps the whole flow readable in one place.

## Consequences

- The flow is easy to follow and test — it lives in one class.
- `booking-service` depends on every leaf service, so circuit breakers there are essential
  ([resilience.md](../resilience.md)).
- Moving parts of the flow to events later is described in
  [future-features.md](../future-features.md).

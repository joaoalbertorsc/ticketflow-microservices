# ADR 0001 — Ticketing domain and service decomposition

- **Status:** Accepted
- **Date:** 2026-10-17

## Context

The project must have at least four independent microservices, each owning its data, and one
business flow that crosses all of them. The domain should have real reasons for distributed
transactions, resilience and caching rather than using them artificially.

## Decision

Use an **event-ticketing** domain, split by business capability:

- `auth-service` — identity (users, roles, tokens)
- `catalog-service` — what is for sale (events, venues, sectors, prices)
- `inventory-service` — how many seats are left, and who holds them
- `payment-service` — money (charges, refunds)
- `notification-service` — telling the user what happened
- `booking-service` — the booking itself, and orchestration of the saga
- `api-gateway` — infrastructure, single entry point

Catalog and inventory are separate on purpose: the catalog is read-heavy and rarely changes,
while inventory is write-heavy and needs strict consistency. They scale and fail differently.

## Consequences

- Six business services exceed the minimum of four and give each pattern a natural home.
- The Create Booking flow touches every service.
- More services means more to run locally; Docker Compose handles it.

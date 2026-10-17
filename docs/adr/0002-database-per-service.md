# ADR 0002 — Database per service

- **Status:** Accepted
- **Date:** 2026-10-17

## Context

The requirement is that each service owns its data, with no relations between tables of
different domains. Physically separate databases are optional.

## Decision

- Each service gets its **own logical database**, which only it accesses:
  - PostgreSQL (one container): `auth_db`, `inventory_db`, `payment_db`, `booking_db`,
    each with its own user and credentials.
  - MongoDB (one container): `catalog_db`, `notification_db`.
  - Redis: cache for `catalog-service` only.
- **No foreign keys across domains.** References to another domain are plain ID columns.
- Data needed later from another domain is **copied as a snapshot** at the moment it is used
  (e.g. unit price and user e-mail on the booking).
- Each relational service manages its own schema with **Flyway** migrations.
- Database choice follows the data: relational where transactions and constraints matter,
  documents where the shape varies (event details) or records are self-contained
  (notifications).

## Consequences

- Stronger isolation than the minimum required (separate databases, not just separate tables).
- A shared Postgres container is a single point of failure locally — acceptable for a dev
  setup; in production each database would be separate.
- Cross-domain consistency is handled by the saga, not by the database.

# ADR 0006 — No service discovery or Config Server (for now)

- **Status:** Accepted
- **Date:** 2026-10-17

## Context

Spring Cloud offers Eureka (service discovery) and Config Server (central configuration).
Both solve problems that appear with many instances and environments.

## Decision

- Services reach each other by **Docker Compose hostname** (e.g. `http://inventory-service:8083`),
  configured per service in `application.yml` and overridable by environment variables.
- Configuration lives with each service (`application.yml` + Spring profiles `local` and
  `docker`). Secrets come from environment variables, never from committed files.

## Consequences

- Fewer moving parts; each service is self-contained.
- Scaling to several instances of one service would need discovery or a load balancer —
  see [future-features.md](../future-features.md).

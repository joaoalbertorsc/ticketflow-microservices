# ADR 0005 — No shared code between services

- **Status:** Accepted
- **Date:** 2026-10-17

## Context

Services exchange the same kinds of data (e.g. a reservation request). A shared "common"
library would avoid duplicating these classes, but would couple every service to it: a change
in the library forces all services to rebuild and redeploy together.

## Decision

- Every service is a **standalone Maven project** with its own `pom.xml`, `Dockerfile` and tests.
- There is **no shared module** and no parent POM between services.
- Each service defines its own copies of the request/response classes it needs. The contract
  between services is the HTTP API, documented in each service's `docs/`.

## Consequences

- Services can be built, versioned and deployed independently.
- Some DTO duplication — an accepted trade-off. Contract tests can later verify that both sides
  agree.

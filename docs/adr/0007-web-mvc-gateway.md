# ADR 0007 — Spring Cloud Gateway, Web MVC flavour

- **Status:** Accepted
- **Date:** 2026-10-18

## Context

The system needs a single entry point that routes public paths to services, checks tokens at the
edge and hides the `/internal/**` endpoints. Spring Cloud Gateway offers a reactive (WebFlux)
flavour and a servlet (Web MVC) flavour.

## Decision

- Use **Spring Cloud Gateway Server Web MVC** (Spring Cloud 2025.1).
- Routes are declarative configuration; paths are forwarded unchanged.
- The gateway only checks that a token is valid. Roles and ownership stay in the services.
- `/internal/**` matches no route, so the gateway answers `404` without revealing it exists.
- Outbound calls use HTTP/1.1 (`spring.http.clients.imperative.factory: simple`).

## Consequences

- One programming model across the whole project (Spring MVC + `SecurityFilterChain`), instead of a
  reactive stack used in one service only.
- Virtual threads (Java 21) cover most of the cost of blocking I/O at the expected load. The reactive
  flavour remains the option for very high connection counts.
- The JDK HTTP client's default HTTP/2 upgrade broke requests with a body. Pinning HTTP/1.1 fixed
  it; the services speak HTTP/1.1 anyway.

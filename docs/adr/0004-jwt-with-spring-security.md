# ADR 0004 — Authentication with Spring Security + JWT

- **Status:** Accepted
- **Date:** 2026-10-17

## Context

The API needs authentication and role-based authorization. There is no frontend yet, so a full
identity provider (Keycloak) adds setup without much benefit at this stage.

## Decision

- `auth-service` handles registration and login, stores users with BCrypt-hashed passwords in
  `auth_db`, and issues **signed JWTs** containing the user id and roles (`USER`, `ADMIN`).
- Tokens are signed with **HS256** and a secret provided by environment variable.
- `api-gateway` validates the token on protected routes.
- Each service validates the token again and enforces roles with `@PreAuthorize`.
- `booking-service` forwards the caller's token on downstream calls.

## Consequences

- Only Spring Security is needed — no extra infrastructure.
- Every service shares the signing secret. Moving to asymmetric keys (RS256) or Keycloak is
  listed in [future-features.md](../future-features.md).

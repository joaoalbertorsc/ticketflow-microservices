# ADR 0008 — "On behalf of" tokens for booking-service's downstream calls

- **Status:** Accepted
- **Date:** 2026-10-18

## Context

Leaf services require a token on their `/internal/**` endpoints, and payment- and
notification-service record the token's subject as the owner of what they create. booking-service
calls them in two situations:

1. during a user's request — it could forward the user's token;
2. from the **recovery job** (finishing compensations, resending notifications), where there is
   no request, so there is no user token to forward. A forwarded user token could also expire
   before a retry happens.

## Decision

booking-service signs its own short-lived token for every downstream call:

- `sub` = the booking's user id, so leaf services record the right owner;
- `roles` = `["SERVICE"]`, `act` = `booking-service`, issuer `ticketflow-auth`;
- valid for 60 seconds, created per call.

## Consequences

- One mechanism for live requests and background recovery.
- Possible only because tokens are HS256 with a secret shared by all services. Any service holding
  the secret could mint tokens: acceptable for this project, not for production.
- The production path is in [future-features.md](../future-features.md): asymmetric keys (only the
  identity provider can sign) and an OAuth2 token-exchange or client-credentials flow for
  service-to-service calls.

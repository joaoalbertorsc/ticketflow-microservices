# Future Features

Ideas intentionally **left out of the current scope**. The current architecture was designed so
each of these can be added later without rewriting the services.

## Messaging (Kafka or RabbitMQ)

- Move notifications to an event-driven flow: `booking-service` publishes `BookingConfirmed`,
  `notification-service` consumes it. Removes a synchronous dependency from the saga.
- **Transactional Outbox** pattern: write the event to an `outbox` table in the same database
  transaction as the state change, then relay it to the broker. Guarantees no lost events.
- Optionally evolve the saga into a **choreography** for some steps and compare both styles.

## Observability

- Metrics with Micrometer + Prometheus, dashboards in Grafana (circuit breaker state, saga
  success/failure rate, latency per service).
- Distributed tracing (Micrometer Tracing + Zipkin or Tempo) to follow one booking across
  every service.
- Centralised logs with a correlation id.

## Identity provider (Keycloak)

- Replace the in-house `auth-service` token issuing with Keycloak (OAuth2 / OpenID Connect).
- Services become pure resource servers validating tokens via JWKS.
- Switch JWT signing from a shared secret to asymmetric keys (RS256) as a first step.
- Replace booking-service's self-signed "on behalf of" tokens with an OAuth2 token exchange or
  client-credentials flow, so no service other than the identity provider can sign tokens.

## Platform

- **Service discovery** (Eureka) when services scale to several instances.
- **Config Server** to centralise configuration and change it without redeploying.
- Kubernetes manifests / Helm charts.

## More resilience

- Retry with exponential backoff on idempotent calls.
- Bulkheads to isolate thread pools per downstream service.
- Rate limiting at the gateway.

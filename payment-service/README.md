# payment-service

> Status: **not started**

**Responsibility:** Simulated charges and refunds, idempotent by booking id. Has a chaos mode to demo the circuit breaker.

| | |
|---|---|
| Port | 8084 |
| Data store | PostgreSQL `payment_db` |

Internal design, data model and API contract will live in [docs/](docs/).
See the [architecture overview](../docs/architecture.md) for how this service fits in.

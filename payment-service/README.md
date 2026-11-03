**English** | [Português (Brasil)](README.pt-BR.md)

# payment-service

> Status: **done**

**Responsibility:** simulated charges and refunds for the booking saga, idempotent by booking id,
plus a chaos mode to demonstrate the circuit breaker in `booking-service`.

| | |
|---|---|
| Port | 8084 |
| Data store | PostgreSQL `payment_db` |
| Design | [docs/design.md](docs/design.md) |

See the [architecture overview](../docs/architecture.md) for how this service fits in.

## Endpoints

| Method | Path | Access |
|---|---|---|
| `POST` | `/internal/payments` | authenticated — `{ bookingId, amount, currency, cardToken }` |
| `POST` | `/internal/payments/booking/{bookingId}/refund` | authenticated |
| `GET` | `/internal/payments/booking/{bookingId}` | authenticated |
| `GET` | `/api/payments/booking/{bookingId}` | owner or `ADMIN` |
| `GET` / `PUT` / `DELETE` | `/api/payments/admin/chaos` | `ADMIN` — `{ "failureRate": 0.5, "latencyMs": 2000 }` |

Test cards: `tok_declined` (insufficient funds), `tok_fraud` (suspected fraud), anything else is
approved; amounts above 50 000.00 are declined.

## Run

From the repository root:

```bash
docker compose up -d --build
```

> Postgres volume created before this service existed? Create its database in place:
>
> ```bash
> docker compose exec postgres psql -U postgres -c "CREATE USER payment_user WITH PASSWORD 'payment_pass'" -c "CREATE DATABASE payment_db OWNER payment_user"
> ```

## Try it

```bash
TOKEN=...   # a user's token from auth-service
curl -X POST localhost:8084/internal/payments -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"bookingId":"<uuid>","amount":690.00,"currency":"BRL","cardToken":"tok_visa"}'

ADMIN=...   # the admin's token
curl -X PUT localhost:8084/api/payments/admin/chaos -H "Authorization: Bearer $ADMIN" \
  -H "Content-Type: application/json" -d '{"failureRate":1.0,"latencyMs":0}'
```

## Tests

```bash
./mvnw test
```

- Unit tests: payment transitions, simulated gateway rules, chaos, service idempotency and voiding
- Web slice tests: declined = `201`, chaos = `503`, owner vs others vs admin, admin-only chaos
- Integration tests (Docker must be running): real PostgreSQL — charge/refund, no second gateway
  call on retry, refund before charge, **a refund overtaking a slow in-flight charge**, chaos writes
  nothing, refunds unaffected by chaos
- **Concurrency test:** 20 identical charges at once → the gateway is called exactly once

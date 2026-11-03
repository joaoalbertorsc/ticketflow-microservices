# inventory-service

> Status: **done**

**Responsibility:** seats per sector and reservations (hold → confirm / release). Guarantees no
overselling, even under heavy concurrency.

| | |
|---|---|
| Port | 8083 |
| Data store | PostgreSQL `inventory_db` |
| Design | [docs/design.md](docs/design.md) |

See the [architecture overview](../docs/architecture.md) for how this service fits in.

## Endpoints

| Method | Path | Access |
|---|---|---|
| `GET` | `/api/inventory/events/{eventId}/sectors` | public |
| `GET` | `/api/inventory/events/{eventId}/sectors/{sectorId}` | public |
| `PUT` | `/api/inventory/events/{eventId}/sectors/{sectorId}` | `ADMIN` — `{ "totalSeats": 500 }` |
| `POST` | `/internal/reservations` | authenticated — `{ bookingId, eventId, sectorId, quantity }` |
| `GET` | `/internal/reservations/booking/{bookingId}` | authenticated |
| `POST` | `/internal/reservations/booking/{bookingId}/confirm` | authenticated |
| `POST` | `/internal/reservations/booking/{bookingId}/release?reason=COMPENSATION\|CANCELLED` | authenticated |

Every `/internal` call is keyed by `bookingId` and safe to repeat.

## Run

From the repository root:

```bash
docker compose up -d --build
./scripts/seed.sh
```

> Postgres volume created before this service existed? Its init script only runs on a fresh
> volume. Either recreate it (`docker compose down -v`, which wipes local data), or create the
> database in place, keeping your data:
>
> ```bash
> docker compose exec postgres psql -U postgres -c "CREATE USER inventory_user WITH PASSWORD 'inventory_pass'" -c "CREATE DATABASE inventory_db OWNER inventory_user"
> ```

From the IDE, with Postgres from compose:

```bash
docker compose up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Try it

```bash
TOKEN=...   # any user's token from auth-service
curl -X POST localhost:8083/internal/reservations -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"bookingId":"3f2b...","eventId":"<event>","sectorId":"balcao","quantity":2}'
curl localhost:8083/api/inventory/events/<event>/sectors
```

## Tests

```bash
./mvnw test
```

- Unit tests: reservation state machine, idempotency rules, expiry handling
- Web slice tests: public / admin / internal access, validation, error mapping
- Integration tests (Docker must be running): real PostgreSQL — seat accounting through the whole
  lifecycle, repeated calls, confirm after expiry, the expiry job, resizing
- **Concurrency test:** 50 parallel buyers, 10 seats → exactly 10 succeed, `available_seats = 0`

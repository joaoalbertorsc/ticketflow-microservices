# catalog-service

> Status: **done**

**Responsibility:** venues, events, sectors and prices. Read-heavy, cached in Redis.

| | |
|---|---|
| Port | 8082 |
| Data store | MongoDB `catalog_db` + Redis cache |
| Design | [docs/design.md](docs/design.md) |

See the [architecture overview](../docs/architecture.md) for how this service fits in.

## Endpoints

| Method | Path | Access |
|---|---|---|
| `GET` | `/api/events?type=&city=&from=&to=&page=&size=` | public |
| `GET` | `/api/events/{id}` | public (cached) |
| `GET` | `/api/venues` | public |
| `POST` | `/api/venues` | `ADMIN` |
| `POST` | `/api/events` | `ADMIN` |
| `PUT` | `/api/events/{id}` | `ADMIN` (draft only) |
| `POST` | `/api/events/{id}/publish` | `ADMIN` |
| `POST` | `/api/events/{id}/cancel` | `ADMIN` |
| `GET` | `/api/admin/events?status=` | `ADMIN` |
| `GET` | `/api/admin/events/{id}` | `ADMIN` |
| `GET` | `/internal/events/{eventId}/sectors/{sectorId}` | authenticated (service-to-service, cached) |

## Run

From the repository root:

```bash
docker compose up -d --build
```

Then fill it with sample venues and events (needs auth-service for the admin token):

```bash
./scripts/seed.sh
```

From the IDE, with MongoDB and Redis from compose:

```bash
docker compose up -d mongo redis
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Try it

```bash
curl "localhost:8082/api/events?city=sao%20paulo"
curl localhost:8082/api/events/<id>
docker compose exec redis redis-cli --scan --pattern '*'   # see what is cached
```

## Tests

```bash
./mvnw test
```

- Unit tests: `Event` lifecycle rules, query and command services
- Web slice tests: public vs admin access, polymorphic `details` JSON, validation, error mapping
- Integration tests (Docker must be running): MongoDB + Redis in Testcontainers — documents and
  `Decimal128` prices as stored, cache hit and eviction, and a run with **Redis unreachable** proving
  the catalog still answers from MongoDB

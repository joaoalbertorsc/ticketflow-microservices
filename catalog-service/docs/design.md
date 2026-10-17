# catalog-service — Design (micro view)

Owns **what is for sale**: venues, events, the sectors of each event and their prices.
It is the most read part of the system, so event lookups are cached in Redis.

## Responsibilities

- Admins manage venues and events (create, edit while in draft, publish, cancel).
- Anyone can browse published events and see their details, without logging in.
- Provide `booking-service` with the data it needs to sell a seat: is the event on sale, which
  sector, what price.

**Not** its job: how many seats are left. Seat counts and reservations belong to
`inventory-service`. The catalog says *what* is sold and *for how much*; inventory says *how many*
are left. Keeping these apart means a burst of bookings (heavy writes on inventory) never slows
down browsing (heavy reads on the catalog).

## Tech

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 4.1.1 |
| Web | Spring Web MVC + Bean Validation |
| Persistence | Spring Data MongoDB, database `catalog_db` |
| Cache | Spring Cache + Spring Data Redis, JSON values, TTL 10 min |
| Security | Spring Security resource server, same JWT secret as `auth-service` |
| Errors | RFC 9457 `ProblemDetail` |
| Tests | JUnit 5, Mockito, `@WebMvcTest`, Testcontainers (MongoDB + Redis) |

## Why MongoDB here

Events differ by type. A concert has a lineup and a genre; a football match has two teams and a
league; a play has a director and a cast. In a relational schema this means either many nullable
columns or extra tables per type. In MongoDB each event is **one document**, and its `details`
field has the shape of its own type. One read returns everything needed to show the event page.

## Document model (`catalog_db`)

```mermaid
erDiagram
    venues {
        string id PK
        string name
        string city
        string address
    }
    events {
        string id PK
        string name
        string type "CONCERT | SPORTS | THEATER"
        string description
        date startsAt
        string status "DRAFT | PUBLISHED | CANCELLED"
        object venue "embedded copy: venueId, name, city"
        array sectors "embedded: id, name, price"
        object details "shape depends on type"
        long version "optimistic locking"
    }
    venues ||--o{ events : "hosts (copied into event)"
```

Example event document:

```json
{
  "_id": "6650f1…",
  "name": "Rock in Rio — Night 1",
  "type": "CONCERT",
  "startsAt": "2026-12-12T20:00:00Z",
  "status": "PUBLISHED",
  "venue":   { "venueId": "6650e0…", "name": "Parque Olímpico", "city": "Rio de Janeiro" },
  "sectors": [
    { "id": "pista",   "name": "Pista",        "price": 450.00 },
    { "id": "premium", "name": "Pista Premium", "price": 890.00 }
  ],
  "details": { "_class": "com.ticketflow.catalog.domain.ConcertDetails", "lineup": ["Band A", "Band B"], "genre": "Rock" },
  "version": 3
}
```

In the **API** the details carry a `type` field instead of `_class`, and the event's type is taken
from it — there is no separate top-level `type` to keep in sync:

```json
"details": { "type": "CONCERT", "lineup": ["Band A", "Band B"], "genre": "Rock" }
```

Design choices:
- **Sectors are embedded** — they never exist without their event and are always read with it.
- **Venue is copied into the event** (name, city), not just referenced: showing an event needs no
  second query. Changing a venue's name later doesn't rewrite past events, which is the correct
  behaviour for a ticket that was already sold.
- **`details` is polymorphic** — `ConcertDetails`, `SportsDetails`, `TheaterDetails`, each a Java
  record implementing a sealed interface; MongoDB stores the class in `_class`.
- **Prices are `BigDecimal`** (stored as `Decimal128`), never `double`. Single currency: BRL.
- **`@Version`** prevents two admins overwriting each other's edits.
- Indexes: `{status, startsAt}` for browsing, `{type}`, `{venue.city}`.

## Event lifecycle

```mermaid
stateDiagram-v2
    [*] --> DRAFT: create
    DRAFT --> DRAFT: edit
    DRAFT --> PUBLISHED: publish (needs ≥ 1 sector, future date)
    PUBLISHED --> CANCELLED: cancel
    DRAFT --> CANCELLED: cancel
    CANCELLED --> [*]
```

Only **draft** events can be edited. Once published, sectors and prices are frozen, so what the
user sees is what the orchestrator charges. Changing a published event means cancelling it and
creating a new one.

## API

| Method | Path | Access | Description |
|---|---|---|---|
| `GET` | `/api/events` | public | Published events, paged. Filters: `type`, `city`, `from`, `to` |
| `GET` | `/api/events/{id}` | public | One published event (cached) |
| `POST` | `/api/events` | `ADMIN` | Create event (`DRAFT`) |
| `PUT` | `/api/events/{id}` | `ADMIN` | Edit a draft event |
| `POST` | `/api/events/{id}/publish` | `ADMIN` | Put on sale |
| `POST` | `/api/events/{id}/cancel` | `ADMIN` | Cancel |
| `GET` | `/api/admin/events` | `ADMIN` | All events in any status |
| `POST` | `/api/venues` | `ADMIN` | Create venue |
| `GET` | `/api/venues` | public | List venues |
| `GET` | `/internal/events/{eventId}/sectors/{sectorId}` | authenticated | Sale snapshot for the orchestrator (cached) |

Internal response (what `booking-service` stores as its snapshot):

```json
{
  "eventId": "6650f1…", "eventName": "Rock in Rio — Night 1", "startsAt": "2026-12-12T20:00:00Z",
  "venueName": "Parque Olímpico", "sectorId": "pista", "sectorName": "Pista",
  "unitPrice": 450.00, "currency": "BRL", "onSale": true
}
```

`onSale` is true only when the event is `PUBLISHED` and has not started yet. The orchestrator
rejects the booking when it is false.

### Errors

| Case | Status |
|---|---|
| Invalid body (blank name, past date, negative price, duplicate sector id, details not matching type) | `400` |
| Event / venue / sector not found, or event not published (public endpoints) | `404` |
| Editing a non-draft event, publishing an invalid event, cancelling a cancelled event | `409` |
| Concurrent edit (version mismatch) | `409` |
| Missing token on admin endpoints / not admin | `401` / `403` |

Admin routes are checked in the security filter chain, **before** the request body is read, so a
non-admin always gets `403` and never sees validation errors. `@PreAuthorize` on the controllers is a
second layer.

## Caching (Redis)

```mermaid
sequenceDiagram
    participant C as Caller
    participant S as EventQueryService
    participant R as Redis
    participant M as MongoDB

    C->>S: getPublishedEvent(id)
    S->>R: GET events::id
    alt cache hit
        R-->>S: event JSON
    else cache miss
        S->>M: findById(id)
        M-->>S: event
        S->>R: SET events::id (TTL 10 min)
    end
    S-->>C: event
    Note over S,R: publish / cancel / edit → evict events::id and sale snapshots
```

- Cached: single event by id (`events`) and sale snapshots (`event-sectors`). Not cached: the
  paged list (many filter combinations, hard to invalidate correctly).
- Every write that changes an event **evicts** its cache entries, so readers never see a stale
  price for longer than the write itself.
- **Redis down ≠ catalog down.** A `CacheErrorHandler` logs cache errors and lets the call go to
  MongoDB. Redis timeouts are 500 ms, and the readiness probe only checks MongoDB, so the service
  stays "ready" without Redis. The cache is an optimisation, never a dependency.

**Known limitation (measured):** while Redis is down, every cached read still pays the 500 ms
timeout before falling back. Worse, locally in Docker Desktop, if the catalog starts while Redis is
already stopped, looking up the `redis` hostname takes ~5 s to fail, so reads take ~8 s. A circuit
breaker around the cache (skip Redis for a while after repeated failures) would make the fallback
immediate.

## Internal structure

```mermaid
flowchart TB
    subgraph api[api — HTTP layer]
        EventController
        AdminEventController
        VenueController
        InternalEventController
        GlobalExceptionHandler
    end
    subgraph application[application — use cases]
        EventQueryService["EventQueryService (reads, cached)"]
        EventCommandService["EventCommandService (writes, evicts)"]
        VenueService
    end
    subgraph domain[domain]
        Event
        Sector
        EventDetails[["EventDetails «sealed interface»"]]
        Venue
        EventRepository[[EventRepository]]
        VenueRepository[[VenueRepository]]
    end
    subgraph infrastructure[infrastructure]
        SecurityConfig
        CacheConfig["CacheConfig + CacheErrorHandler"]
        MongoConfig["MongoConfig (indexes, Decimal128)"]
    end

    api --> application
    application --> domain
    infrastructure -.configures.-> application
```

Package root: `com.ticketflow.catalog`.

**SOLID in practice**
- *Single responsibility / CQRS-lite:* reads (`EventQueryService`, cached) and writes
  (`EventCommandService`, evicting) are separate classes, so caching rules live in one place each.
- *Open/closed:* a new event type (e.g. `FestivalDetails`) is a new record implementing the sealed
  `EventDetails` interface; nothing else changes.
- *Rich domain:* `Event` enforces its own rules (`publish()`, `cancel()`, `editable()`), so
  controllers and services can't put it in an invalid state.

## Configuration

| Env var | Default (local) | Purpose |
|---|---|---|
| `SERVER_PORT` | `8082` | HTTP port |
| `MONGODB_URI` | `mongodb://catalog_user:catalog_pass@localhost:27017/catalog_db` | Database |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Cache |
| `CACHE_TTL` | `10m` | Cache lifetime |
| `JWT_SECRET` | none (dev value in `application-local.yml`) | Token validation |

## Tests

- **Unit:** `Event` lifecycle rules, `EventCommandService`, `EventQueryService` (Mockito).
- **Web slice:** controllers — validation, public vs admin access, status codes.
- **Integration:** MongoDB + Redis in Testcontainers — create → publish → browse; polymorphic
  `details` round-trip; second read served from Redis; cache evicted after cancel; the service
  still answers when Redis is stopped.

## Delivered with this service

- `catalog-service/` Maven project and `Dockerfile`
- `mongo` and `redis` added to `docker-compose.yml`, with `infra/mongo/init.js` creating
  `catalog_db` and its own user
- A seed script with sample venues and events, to make manual testing easy

## Future

- Publish an `EventPublished` message so `inventory-service` creates the seat stock
  automatically (needs messaging — see [future features](../../docs/future-features.md)). For now
  an admin sets the seat count per sector in `inventory-service`.

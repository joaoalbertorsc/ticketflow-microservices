# TicketFlow — Microservices Ticketing Backend

An event-ticketing backend built as independent **Spring Boot** microservices to practise
microservice architecture: database per service, an orchestrated **saga** with compensations,
**Resilience4j circuit breakers** with fallbacks, JWT security and SOLID design.

> Backend only — test it with Postman / curl.

## Services

| Service | Port | Data store | Status |
|---|---|---|---|
| [api-gateway](api-gateway/) | 8080 | — | not started |
| [auth-service](auth-service/) | 8081 | PostgreSQL | done |
| [catalog-service](catalog-service/) | 8082 | MongoDB + Redis | not started |
| [inventory-service](inventory-service/) | 8083 | PostgreSQL | not started |
| [payment-service](payment-service/) | 8084 | PostgreSQL | not started |
| [notification-service](notification-service/) | 8085 | MongoDB | not started |
| [booking-service](booking-service/) | 8086 | PostgreSQL | not started |

## Tech stack

- Java 21, Spring Boot, Maven
- Spring Web, Spring Data JPA, Spring Data MongoDB, Spring Data Redis
- Spring Security + JWT
- Spring Cloud Gateway
- Resilience4j (circuit breaker + fallback)
- PostgreSQL, MongoDB, Redis, Flyway
- JUnit 5, Mockito, Testcontainers
- Docker, Docker Compose

## Documentation

- [Architecture overview](docs/architecture.md) — services, diagram, communication and data rules
- [Booking saga](docs/booking-saga.md) — the flow that crosses every service, compensations, state machines
- [Resilience](docs/resilience.md) — circuit breakers, fallbacks, chaos demo
- [Architecture Decision Records](docs/adr/)
- [Future features](docs/future-features.md)

Each service also has its own `docs/` folder with its internal design.

## Repository layout

```
├── docs/                 # macro view: architecture, saga, resilience, ADRs
├── infra/                # shared local infrastructure (database init scripts)
├── <service>/            # one standalone Maven project per service, each with its own docs/
└── docker-compose.yml    # runs everything locally (added as services are built)
```

## Running locally

Requires Docker. Optionally copy `.env.example` to `.env` to change secrets.

```bash
docker compose up -d --build
```


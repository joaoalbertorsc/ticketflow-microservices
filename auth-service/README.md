**English** | [Português (Brasil)](README.pt-BR.md)

# auth-service

> Status: **done**

**Responsibility:** users, roles, registration, login and JWT issuing.

| | |
|---|---|
| Port | 8081 |
| Data store | PostgreSQL `auth_db` |
| Design | [docs/design.md](docs/design.md) |

See the [architecture overview](../docs/architecture.md) for how this service fits in.

## Endpoints

| Method | Path | Access |
|---|---|---|
| `POST` | `/api/auth/register` | public |
| `POST` | `/api/auth/login` | public |
| `GET` | `/api/auth/me` | authenticated |
| `PUT` | `/api/users/{id}/roles` | `ADMIN` |
| `GET` | `/internal/users/{id}` | authenticated (service-to-service) |
| `GET` | `/actuator/health` | public |

## Run

With Docker (from the repository root):

```bash
docker compose up -d --build postgres auth-service
```

From the IDE / command line, with Postgres from compose and the `local` profile:

```bash
docker compose up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Try it

```bash
curl -X POST localhost:8081/api/auth/register -H "Content-Type: application/json" \
  -d '{"name":"Ana Souza","email":"ana@mail.com","password":"Str0ngPass"}'

curl -X POST localhost:8081/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"ana@mail.com","password":"Str0ngPass"}'

curl localhost:8081/api/auth/me -H "Authorization: Bearer <accessToken>"
```

The first admin (`admin@ticketflow.local` / `Admin12345` by default) is created at startup.

## Tests

```bash
./mvnw test
```

- Unit tests: `UserService`, `AuthenticationService`, `JwtTokenIssuer`
- Web slice tests: validation, status codes and security rules of every controller
- Integration test: full app against PostgreSQL in Testcontainers (Docker must be running)

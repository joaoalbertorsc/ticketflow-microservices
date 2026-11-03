**English** | [Português (Brasil)](README.pt-BR.md)

# api-gateway

> Status: **done**

**Responsibility:** the single entry point. Routes each public path to its service, rejects
requests without a valid token at the edge, never exposes `/internal/**`, and tags every request
with an `X-Request-Id`.

| | |
|---|---|
| Port | 8080 |
| Data store | — |
| Design | [docs/design.md](docs/design.md) |

See the [architecture overview](../docs/architecture.md) for how this service fits in.

## Routes

| Path | Service |
|---|---|
| `/api/auth/**`, `/api/users/**` | auth-service |
| `/api/events/**`, `/api/venues/**`, `/api/admin/events/**` | catalog-service |
| `/api/inventory/**` | inventory-service |
| `/api/payments/**` | payment-service |
| `/api/notifications/**`, `/api/admin/notifications/**` | notification-service |
| `/api/bookings/**`, `/api/admin/bookings/**` | booking-service |
| `/internal/**` | never routed — `404` |

Routes are configuration, in `src/main/resources/application.yml`. The Java code is only the
security rules (`SecurityConfig`) and the request-id filter (`RequestIdFilter`).

## Run

From the repository root:

```bash
docker compose up -d --build
./scripts/demo.sh           # every API call goes through localhost:8080
```

## Try it

```bash
curl localhost:8080/api/events                     # public
curl -i localhost:8080/api/bookings/me             # 401 at the gateway, never reaches booking-service
curl -i localhost:8080/internal/users/x            # 404: internal endpoints are not exposed
docker compose logs api-gateway | grep access      # one line per request, with its X-Request-Id
```

## Tests

```bash
./mvnw test
```

`GatewayTest` runs the real gateway in front of six WireMock servers, one per service:
- every route reaches the right service, with path, query, body and headers unchanged;
- public routes need no token; protected ones get `401` without a token, or with a forged, expired
  or foreign one, and the request never reaches the service;
- `/internal/**` is `404` even with a valid token;
- request ids are generated, kept when sane, replaced when not, and returned on rejections too.

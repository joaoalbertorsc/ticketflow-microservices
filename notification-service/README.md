# notification-service

> Status: **done**

**Responsibility:** simulated e-mail notifications (booking confirmed / failed / cancelled), each
stored as a self-contained document and never sent twice for the same booking.

| | |
|---|---|
| Port | 8085 |
| Data store | MongoDB `notification_db` |
| Design | [docs/design.md](docs/design.md) |

See the [architecture overview](../docs/architecture.md) for how this service fits in.

## Endpoints

| Method | Path | Access |
|---|---|---|
| `POST` | `/internal/notifications` | authenticated — `{ bookingId, type, recipient, data }` |
| `GET` | `/internal/notifications/booking/{bookingId}` | authenticated |
| `GET` | `/api/notifications/me?page=&size=` | authenticated (own notifications) |
| `GET` | `/api/admin/notifications?bookingId=&page=&size=` | `ADMIN` |

Addresses ending in `@bounce.test` are rejected by the simulated sender (stored as `FAILED`).

## Run

From the repository root:

```bash
docker compose up -d --build
docker compose logs -f notification-service   # the "sent" e-mails appear here
```

> MongoDB volume created before this service existed? Create its user in place:
>
> ```bash
> docker compose exec mongo mongosh -u root -p root --quiet --eval "db.getSiblingDB('notification_db').createUser({user:'notification_user',pwd:'notification_pass',roles:[{role:'readWrite',db:'notification_db'}]})"
> ```

## Try it

```bash
TOKEN=...   # a user's token from auth-service
curl -X POST localhost:8085/internal/notifications -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{
    "bookingId": "<uuid>", "type": "BOOKING_FAILED",
    "recipient": {"name": "Ana", "email": "ana@mail.com"},
    "data": {"eventName": "Hamlet", "reason": "Card declined"}}'
curl localhost:8085/api/notifications/me -H "Authorization: Bearer $TOKEN"
```

## Tests

```bash
./mvnw test
```

- Unit tests: each template, required fields read from placeholders, registry completeness,
  claim-then-send idempotency, bounces
- Web slice tests: validation, `201` vs `200`, own notifications only, admin-only search
- Integration tests (Docker must be running): real MongoDB — stored document shape and UUID
  format, unique index, separate types per booking, **10 identical concurrent requests send one
  e-mail**, newest-first paging per user

# notification-service — Design (micro view)

Owns **telling the user what happened**: booking confirmed, booking failed, booking cancelled.
E-mail is simulated (the message is rendered, stored and logged, not actually sent), but each
notification is a complete, self-contained record of what the user was told.

It is the **least critical step** of the saga, which makes it the clearest place to show a
fallback that *doesn't* undo anything: if this service is down, the booking still succeeds and
`booking-service` keeps the notification as `PENDING`.

## Responsibilities

- Receive a notification request from `booking-service`, render subject and body from a template,
  "send" it, and store the result.
- Never send the same notification twice for the same booking.
- Let users read their own notifications; let admins look up notifications of any booking.

**Not** its job: deciding *when* to notify (the orchestrator does), retrying `PENDING`
notifications (booking-service's side — see [future features](../../docs/future-features.md)),
or knowing anything about events, seats or payments beyond the text it is given.

## Tech

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 4.1.1 |
| Web | Spring Web MVC + Bean Validation |
| Persistence | Spring Data MongoDB, database `notification_db` |
| Security | Spring Security resource server, same JWT secret as `auth-service` |
| Errors | RFC 9457 `ProblemDetail` |
| Tests | JUnit 5, Mockito, `@WebMvcTest`, Testcontainers (MongoDB) |

## Why MongoDB here

A notification is written once and then only read. It is naturally **one document**: recipient,
rendered subject and body, the data used to render it, and its delivery status. It is never
joined with anything, and the `data` it carries differs by notification type (a failure has a
reason, a confirmation has a total amount). A document store fits that shape directly.

## Document model (`notification_db`)

```mermaid
erDiagram
    notifications {
        string id PK
        uuid bookingId "from booking-service, no reference"
        uuid userId "from the JWT, for 'my notifications'"
        string type "BOOKING_CONFIRMED | BOOKING_FAILED | BOOKING_CANCELLED"
        string channel "EMAIL"
        object recipient "name, email"
        object data "values used by the template"
        string subject "rendered"
        string body "rendered"
        string status "SENDING | SENT | FAILED"
        string failureReason
        date createdAt
        date sentAt
    }
```

Indexes:
- `UNIQUE {bookingId, type}` — the idempotency key: one confirmation, one failure notice, one
  cancellation per booking at most.
- `{userId, createdAt: -1}` — "my notifications", newest first.

Example document:

```json
{
  "_id": "6650f1…",
  "bookingId": "1a2a0a5b-…", "userId": "38dfdb28-…",
  "type": "BOOKING_CONFIRMED", "channel": "EMAIL",
  "recipient": { "name": "Ana Souza", "email": "ana@mail.com" },
  "data": { "eventName": "Hamlet", "startsAt": "2026-12-01T20:00:00Z", "venueName": "Theatro Municipal",
            "sectorName": "Plateia", "quantity": 2, "totalAmount": "240.00", "currency": "BRL" },
  "subject": "Your tickets for Hamlet are confirmed",
  "body": "Hi Ana Souza, ... 2 x Plateia ... Total: BRL 240.00 ...",
  "status": "SENT", "createdAt": "…", "sentAt": "…"
}
```

## Templates

One template per type, in code (Java text blocks with named placeholders). No extra template
engine is needed for three short e-mails.

| Type | Subject | Required data |
|---|---|---|
| `BOOKING_CONFIRMED` | Your tickets for {eventName} are confirmed | eventName, startsAt, venueName, sectorName, quantity, totalAmount, currency |
| `BOOKING_FAILED` | We couldn't complete your booking for {eventName} | eventName, reason |
| `BOOKING_CANCELLED` | Your booking for {eventName} was cancelled | eventName, totalAmount, currency |

A request missing a field its template needs is rejected with `400`, naming the missing fields.
Templates are a strategy per type: an abstract `NotificationTemplate` with one subclass per type.
The fields a template requires are **read from its own placeholders**, so the validation can never
drift from the text. Adding a type means adding one class; `TemplateRegistry` fails at startup if a
type has no template.

## Sending (simulated)

`EmailSender` is an interface (the "port"); `LoggingEmailSender` implements it by writing the
rendered e-mail to the log. To show the `FAILED` path deterministically, any address ending in
`@bounce.test` is rejected by the simulator: the notification is stored with status `FAILED` and
a reason. That is a **delivery result, not a service error** — the response is still `201`, the
same way a declined card is in payment-service.

## Idempotency

| Call | Result |
|---|---|
| first `(bookingId, type)` | rendered, sent, stored → `201` |
| same `(bookingId, type)` again | the stored notification → `200`, **not sent again** |
| two identical requests at the same instant | only one reaches the sender (see below); the other gets the stored one → `200` |

The notification is **stored first, as `SENDING`**, and only then delivered and marked `SENT` or
`FAILED`. Inserting it claims the `(bookingId, type)` slot: if two identical requests race, the
unique index rejects the second insert, so the second request never calls the e-mail sender.
Sending first and storing afterwards would let both requests send before either one saves.

This makes the orchestrator's retries (and a future `PENDING` resend job) safe: a user never
gets the same e-mail twice.

## API

| Method | Path | Access | Description |
|---|---|---|---|
| `POST` | `/internal/notifications` | authenticated | Send `{ bookingId, type, recipient, data }` |
| `GET` | `/internal/notifications/booking/{bookingId}` | authenticated | Notifications of one booking |
| `GET` | `/api/notifications/me?page=&size=` | authenticated | The caller's notifications, newest first |
| `GET` | `/api/admin/notifications?bookingId=&page=&size=` | `ADMIN` | Any notifications |

The `userId` stored is the `sub` of the caller's token. booking-service forwards the user's
token, so it is the user who booked.

Request example:

```json
{
  "bookingId": "1a2a0a5b-…",
  "type": "BOOKING_CONFIRMED",
  "recipient": { "name": "Ana Souza", "email": "ana@mail.com" },
  "data": { "eventName": "Hamlet", "startsAt": "2026-12-01T20:00:00Z", "venueName": "Theatro Municipal",
            "sectorName": "Plateia", "quantity": 2, "totalAmount": "240.00", "currency": "BRL" }
}
```

### Errors

| Case | Status |
|---|---|
| Invalid body (bad e-mail, unknown type, missing template fields) | `400` |
| Missing token / not admin | `401` / `403` (admin routes checked before the body is read) |

## Internal structure

```mermaid
flowchart TB
    subgraph api[api — HTTP layer]
        InternalNotificationController
        NotificationController
        AdminNotificationController
        GlobalExceptionHandler
    end
    subgraph application[application — use cases]
        NotificationService
        TemplateRegistry
        NotificationTemplate[["NotificationTemplate «abstract»<br/>Confirmed / Failed / Cancelled"]]
        EmailSender[["EmailSender «interface»"]]
    end
    subgraph domain[domain]
        Notification
        NotificationType
        NotificationRepository[[NotificationRepository]]
    end
    subgraph infrastructure[infrastructure]
        LoggingEmailSender
        MongoNotificationRepository
        SecurityConfig
    end

    api --> application
    application --> domain
    LoggingEmailSender -.implements.-> EmailSender
    MongoNotificationRepository -.implements.-> NotificationRepository
```

Package root: `com.ticketflow.notification`.

**SOLID in practice**
- *Open/closed + strategy:* each notification type is one `NotificationTemplate` class; the
  service picks it from a registry and never switches on the type.
- *Dependency inversion:* `NotificationService` sends through `EmailSender`; swapping the logger for
  SMTP or a provider (SES, SendGrid) is one new class.
- *Single responsibility:* templates only render, the sender only delivers, the service only
  orchestrates and keeps it idempotent.

## Configuration

| Env var | Default (local) | Purpose |
|---|---|---|
| `SERVER_PORT` | `8085` | HTTP port |
| `MONGODB_URI` | `mongodb://notification_user:notification_pass@localhost:27017/notification_db` | Database |
| `JWT_SECRET` | none (dev value in `application-local.yml`) | Token validation |

## Tests

- **Unit:** each template renders the right subject/body and rejects missing fields; the service
  doesn't send twice; bounce addresses produce `FAILED` (Mockito).
- **Web slice:** validation, `201` vs `200`, "my notifications" only returns the caller's, admin-only
  routes.
- **Integration (MongoDB in Testcontainers):** stored document shape, unique `{bookingId, type}`
  index, repeated and concurrent identical requests send once, paging newest first.

## Delivered with this service

- `notification-service/` Maven project and `Dockerfile`
- `notification_db` user added to `infra/mongo/init.js`, service added to `docker-compose.yml`

## How the saga uses it

The notification step is wrapped in the `notificationService` circuit breaker in booking-service.
When this service is down (stop its container to see it), the fallback stores the notification on
the booking as `PENDING` and the booking still ends `CONFIRMED` — see
[resilience.md](../../docs/resilience.md).

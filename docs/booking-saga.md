# Booking Saga (orchestration-based)

The booking flow spans several services, each with its own database, so there is no
single ACID transaction. Instead, `booking-service` runs an **orchestrated saga**: it calls
each step in order, records progress in its own database, and when a step fails it runs
**compensating actions** to undo the steps already done.

## Create Booking — happy path

This is the flow that goes through **every service** in the architecture.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant GW as api-gateway
    participant B as booking-service
    participant A as auth-service
    participant C as catalog-service
    participant I as inventory-service
    participant P as payment-service
    participant N as notification-service

    U->>GW: POST /api/bookings {eventId, sectorId, quantity} + JWT
    GW->>GW: validate JWT
    GW->>B: forward request
    B->>B: create booking (PENDING)
    B->>A: GET /internal/users/{userId}
    A-->>B: name, e-mail (snapshot)
    B->>C: GET /internal/events/{eventId}/sectors/{sectorId}
    C-->>B: event on sale, unit price (snapshot)
    B->>I: POST /internal/reservations {bookingId, sectorId, qty}
    I-->>B: reservationId (HELD, expires in 10 min)
    B->>B: booking SEATS_RESERVED
    B->>P: POST /internal/payments {bookingId, amount}
    P-->>B: paymentId (APPROVED)
    B->>B: booking PAID
    B->>I: POST /internal/reservations/{id}/confirm
    I-->>B: CONFIRMED
    B->>N: POST /internal/notifications {BOOKING_CONFIRMED}
    N-->>B: SENT
    B->>B: booking CONFIRMED
    B-->>GW: 201 Created
    GW-->>U: booking details
```

## Failure: payment declined or payment-service down → compensation

```mermaid
sequenceDiagram
    autonumber
    participant B as booking-service
    participant I as inventory-service
    participant P as payment-service
    participant N as notification-service

    Note over B: seats already HELD (step done)
    B->>P: POST /internal/payments
    P--xB: declined / timeout / circuit OPEN
    Note over B: compensation starts
    B->>P: POST /internal/payments/booking/{bookingId}/refund (idempotent, no-op if never charged)
    B->>I: POST /internal/reservations/{id}/release
    I-->>B: RELEASED
    B->>B: booking FAILED (reason stored)
    B->>N: POST /internal/notifications {BOOKING_FAILED}
```

The refund call matters when the payment call **timed out**: the orchestrator cannot know
whether the charge happened, so it asks for a refund by `bookingId`. The payment service
refunds if a charge exists and does nothing otherwise.

## Failure: notification-service down → fallback, no compensation

A notification is not worth cancelling a paid booking. The circuit breaker fallback stores
the notification on the booking as `PENDING` and the booking still ends as `CONFIRMED`.

## Cancel Booking

Reverse saga, triggered by `POST /api/bookings/{id}/cancel` on a `CONFIRMED` booking:

1. `payment-service` — refund
2. `inventory-service` — release seats
3. `notification-service` — `BOOKING_CANCELLED` (fallback: `PENDING`)
4. booking → `CANCELLED`

## Booking state machine

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> SEATS_RESERVED: seats held
    PENDING --> FAILED: user/event invalid, no seats
    SEATS_RESERVED --> PAID: payment approved
    SEATS_RESERVED --> FAILED: payment failed (seats released)
    PAID --> CONFIRMED: reservation confirmed
    CONFIRMED --> CANCELLED: user cancels (refund + release)
    FAILED --> [*]
    CANCELLED --> [*]
```

## Reservation state machine (inventory-service)

```mermaid
stateDiagram-v2
    [*] --> HELD
    HELD --> CONFIRMED: confirm
    HELD --> RELEASED: release / hold expired
    CONFIRMED --> RELEASED: booking cancelled
    RELEASED --> [*]
```

## Safety rules

- **Idempotency:** `bookingId` is sent as an idempotency key to `inventory` and `payment`.
  Retrying a step never holds seats twice or charges twice.
- **Hold expiry:** a `HELD` reservation expires after 10 minutes. If the orchestrator crashes
  mid-saga, seats are not locked forever — a scheduled job in `inventory-service` releases them.
- **Saga log:** every step and its result is recorded in `booking_db` (`booking_saga_step`),
  so you can always see how far a booking got and why it failed.
- **Compensations are idempotent** and safe to call more than once.

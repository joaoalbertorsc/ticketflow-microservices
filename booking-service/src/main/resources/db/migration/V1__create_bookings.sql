-- A booking and the snapshot of everything it needs from other services. user_id, event_id,
-- sector_id, reservation_id and payment_id belong to other services: plain columns, never FKs.
CREATE TABLE bookings (
    id                  UUID          PRIMARY KEY,
    user_id             UUID          NOT NULL,
    idempotency_key     VARCHAR(100),
    event_id            VARCHAR(24)   NOT NULL,
    sector_id           VARCHAR(40)   NOT NULL,
    quantity            INTEGER       NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    failure_code        VARCHAR(30),
    failure_reason      VARCHAR(500),
    user_name           VARCHAR(100),
    user_email          VARCHAR(255),
    event_name          VARCHAR(150),
    starts_at           TIMESTAMPTZ,
    venue_name          VARCHAR(100),
    sector_name         VARCHAR(60),
    unit_price          NUMERIC(12, 2),
    total_amount        NUMERIC(12, 2),
    currency            VARCHAR(3),
    reservation_id      UUID,
    payment_id          UUID,
    charge_attempted    BOOLEAN       NOT NULL DEFAULT FALSE,
    notification_status VARCHAR(10)   NOT NULL,
    version             BIGINT        NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_bookings_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_bookings_quantity CHECK (quantity BETWEEN 1 AND 10),
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING', 'SEATS_RESERVED', 'PAID', 'CONFIRMED',
        'COMPENSATING', 'FAILED', 'CANCELLING', 'CANCELLED')),
    CONSTRAINT ck_bookings_notification CHECK (notification_status IN ('NONE', 'SENT', 'PENDING'))
);

CREATE INDEX ix_bookings_user_created ON bookings (user_id, created_at DESC);
-- The recovery job scans unfinished bookings; finished ones (most rows) stay out of this index.
CREATE INDEX ix_bookings_unfinished ON bookings (status, updated_at)
    WHERE status IN ('PENDING', 'SEATS_RESERVED', 'PAID', 'COMPENSATING', 'CANCELLING');
CREATE INDEX ix_bookings_notification_pending ON bookings (updated_at) WHERE notification_status = 'PENDING';

-- The saga log: every step tried for a booking, and how it went.
CREATE TABLE booking_saga_steps (
    id         BIGSERIAL    PRIMARY KEY,
    booking_id UUID         NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    step       VARCHAR(20)  NOT NULL,
    outcome    VARCHAR(10)  NOT NULL,
    detail     VARCHAR(500),
    at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_steps_outcome CHECK (outcome IN ('SUCCEEDED', 'FAILED', 'FALLBACK'))
);

CREATE INDEX ix_saga_steps_booking ON booking_saga_steps (booking_id, id);

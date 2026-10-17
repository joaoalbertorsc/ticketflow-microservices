-- Seats of one sector of one event. event_id / sector_id are ids from catalog-service:
-- plain columns, never foreign keys (that data belongs to another service).
CREATE TABLE sector_stock (
    id              UUID        PRIMARY KEY,
    event_id        VARCHAR(24) NOT NULL,
    sector_id       VARCHAR(40) NOT NULL,
    total_seats     INTEGER     NOT NULL,
    available_seats INTEGER     NOT NULL,
    version         BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_sector_stock_event_sector UNIQUE (event_id, sector_id),
    -- Safety net: whatever the application does, seats can't go negative or above capacity.
    CONSTRAINT ck_sector_stock_seats CHECK (total_seats >= 0 AND available_seats >= 0 AND available_seats <= total_seats)
);

-- A hold on seats for one booking. booking_id comes from booking-service and is the
-- idempotency key of every reservation operation.
CREATE TABLE reservations (
    id             UUID        PRIMARY KEY,
    booking_id     UUID        NOT NULL,
    stock_id       UUID        NOT NULL REFERENCES sector_stock (id),
    event_id       VARCHAR(24) NOT NULL,
    sector_id      VARCHAR(40) NOT NULL,
    quantity       INTEGER     NOT NULL,
    status         VARCHAR(20) NOT NULL,
    release_reason VARCHAR(20),
    expires_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT      NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_reservations_booking UNIQUE (booking_id),
    CONSTRAINT ck_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT ck_reservations_status CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED')),
    CONSTRAINT ck_reservations_reason CHECK (release_reason IN ('COMPENSATION', 'EXPIRED', 'CANCELLED'))
);

-- The expiry job looks for old holds; a partial index keeps that cheap as reservations grow.
CREATE INDEX ix_reservations_held_expires_at ON reservations (expires_at) WHERE status = 'HELD';

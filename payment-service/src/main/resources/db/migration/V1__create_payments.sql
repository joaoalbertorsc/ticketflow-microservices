-- One row per booking. booking_id and user_id come from other services:
-- plain columns, never foreign keys.
CREATE TABLE payments (
    id                UUID          PRIMARY KEY,
    booking_id        UUID          NOT NULL,
    user_id           UUID          NOT NULL,
    amount            NUMERIC(12, 2),
    currency          VARCHAR(3),
    card_token        VARCHAR(64),
    status            VARCHAR(20)   NOT NULL,
    decline_reason    VARCHAR(30),
    gateway_reference VARCHAR(64),
    refunded_at       TIMESTAMPTZ,
    version           BIGINT        NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_payments_booking UNIQUE (booking_id),
    CONSTRAINT ck_payments_status CHECK (status IN ('APPROVED', 'DECLINED', 'REFUNDED', 'VOIDED')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    -- Only a VOIDED tombstone (refund before any charge) has no charge data.
    CONSTRAINT ck_payments_charge_data CHECK (
        status = 'VOIDED' OR (amount IS NOT NULL AND currency IS NOT NULL AND card_token IS NOT NULL)),
    CONSTRAINT ck_payments_refunded_at CHECK ((status = 'REFUNDED') = (refunded_at IS NOT NULL))
);

CREATE INDEX ix_payments_user ON payments (user_id);

-- GlobalPay Lab | Issue #21 | PaymentIntent schema

CREATE TABLE payment_intent
(
    id                 UUID PRIMARY KEY,
    merchant_id        UUID NOT NULL,
    amount             NUMERIC(24, 8) NOT NULL,
    currency           VARCHAR(3) NOT NULL,
    idempotency_key    VARCHAR(128) NOT NULL,
    status             VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    provider_reference VARCHAR(128),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_payment_intent_merchant
        FOREIGN KEY (merchant_id)
            REFERENCES merchant (id)
            ON DELETE RESTRICT,

    CONSTRAINT ck_payment_amount
        CHECK (amount > 0),

    CONSTRAINT ck_payment_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT ck_payment_status
        CHECK (status IN (
            'CREATED',
            'PROCESSING',
            'SUCCEEDED',
            'FAILED'
        )),

    CONSTRAINT ck_payment_idempotency_key
        CHECK (length(btrim(idempotency_key)) > 0),

    CONSTRAINT ck_payment_timestamps
        CHECK (updated_at >= created_at),

    CONSTRAINT uk_payment_merchant_idempotency
        UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_payment_intent_merchant
    ON payment_intent (merchant_id, created_at DESC);

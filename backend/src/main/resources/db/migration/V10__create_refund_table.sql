-- GlobalPay Lab | Issue #27 | Refund schema

CREATE TABLE refund
(
    id                 UUID PRIMARY KEY,
    payment_id         UUID NOT NULL,
    merchant_id        UUID NOT NULL,
    amount             NUMERIC(24, 8) NOT NULL,
    currency           VARCHAR(3) NOT NULL,
    status             VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    provider_reference VARCHAR(128),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_refund_payment
        FOREIGN KEY (payment_id)
            REFERENCES payment_intent (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_refund_merchant
        FOREIGN KEY (merchant_id)
            REFERENCES merchant (id)
            ON DELETE RESTRICT,

    CONSTRAINT uk_refund_payment
        UNIQUE (payment_id),

    CONSTRAINT ck_refund_amount
        CHECK (amount > 0),

    CONSTRAINT ck_refund_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT ck_refund_status
        CHECK (status IN (
            'CREATED',
            'PROCESSING',
            'SUCCEEDED',
            'FAILED'
        )),

    CONSTRAINT ck_refund_timestamps
        CHECK (updated_at >= created_at),

    CONSTRAINT ck_refund_provider_reference
        CHECK (
            provider_reference IS NULL
            OR length(btrim(provider_reference)) > 0
        )
);

CREATE INDEX idx_refund_merchant
    ON refund (merchant_id, created_at DESC);

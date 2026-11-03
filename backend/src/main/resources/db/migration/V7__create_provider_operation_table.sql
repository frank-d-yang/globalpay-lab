CREATE TABLE provider_operation
(
    id                 UUID PRIMARY KEY,
    payment_id         UUID NOT NULL,
    operation_id       VARCHAR(128) NOT NULL,
    amount             NUMERIC(24, 8) NOT NULL,
    currency           VARCHAR(3) NOT NULL,
    status             VARCHAR(16) NOT NULL,
    provider_reference VARCHAR(128),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT uk_provider_operation_id
        UNIQUE (operation_id),

    CONSTRAINT ck_provider_operation_amount
        CHECK (amount > 0),

    CONSTRAINT ck_provider_operation_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT ck_provider_operation_status
        CHECK (status IN (
            'PROCESSING',
            'SUCCEEDED',
            'DECLINED'
        )),

    CONSTRAINT ck_provider_operation_timestamps
        CHECK (updated_at >= created_at)
);

CREATE INDEX idx_provider_operation_payment
    ON provider_operation (payment_id);
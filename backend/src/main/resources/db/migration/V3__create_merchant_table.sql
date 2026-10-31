-- GlobalPay Lab | Issue #20 | Merchant schema

CREATE TABLE merchant
(
    id           UUID PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_merchant_display_name
        CHECK (length(btrim(display_name)) > 0),

    CONSTRAINT ck_merchant_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

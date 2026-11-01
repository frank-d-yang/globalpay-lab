-- GlobalPay Lab | Issue #22 | Merchant API Keys

CREATE TABLE merchant_api_key
(
    id          UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    key_hash    VARCHAR(64) NOT NULL UNIQUE,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,

    CONSTRAINT fk_api_key_merchant
        FOREIGN KEY (merchant_id)
            REFERENCES merchant (id)
            ON DELETE RESTRICT,

    CONSTRAINT ck_api_key_hash
        CHECK (key_hash ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_api_key_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),

    CONSTRAINT ck_api_key_revocation
        CHECK (
            (status = 'ACTIVE' AND revoked_at IS NULL)
            OR
            (status = 'REVOKED' AND revoked_at IS NOT NULL)
        ),

    CONSTRAINT ck_api_key_revocation_time
        CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX idx_merchant_api_key_merchant
    ON merchant_api_key (merchant_id);

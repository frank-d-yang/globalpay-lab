-- GlobalPay Lab | Issue #3 | Ledger Core initial schema
-- PostgreSQL 16; Flyway V1

CREATE TABLE ledger_account
(
    id           UUID PRIMARY KEY,
    owner_type   VARCHAR(16)  NOT NULL,
    owner_id     VARCHAR(128) NOT NULL,
    currency     VARCHAR(3)   NOT NULL,
    account_type VARCHAR(16)  NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_ledger_account_owner_type
        CHECK (owner_type IN (
                              'USER', 'MERCHANT', 'PLATFORM', 'PARTNER'
            )),
    CONSTRAINT ck_ledger_account_owner_id
        CHECK (length(btrim(owner_id)) > 0),
    CONSTRAINT ck_ledger_account_currency
        CHECK (currency ~ '^[A-Z]{3}$'
) ,
    CONSTRAINT ck_ledger_account_account_type
        CHECK (account_type IN (
            'ASSET', 'LIABILITY', 'REVENUE',
            'EXPENSE', 'EQUITY'
        )),
    CONSTRAINT ck_ledger_account_status
        CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE TABLE journal_entry
(
    id         UUID PRIMARY KEY,
    currency   VARCHAR(3)  NOT NULL,
    status     VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at  TIMESTAMPTZ,

    CONSTRAINT ck_journal_entry_currency
        CHECK (currency ~ '^[A-Z]{3}$'
) ,
    CONSTRAINT ck_journal_entry_status
        CHECK (status IN ('DRAFT', 'POSTED')),
    CONSTRAINT ck_journal_entry_posted_at
        CHECK (
            (status = 'DRAFT' AND posted_at IS NULL)
            OR
            (status = 'POSTED' AND posted_at IS NOT NULL)
        ),
    CONSTRAINT ck_journal_entry_posted_order
        CHECK (posted_at IS NULL OR posted_at >= created_at)
);

CREATE TABLE journal_line
(
    id                UUID PRIMARY KEY,
    journal_entry_id  UUID           NOT NULL,
    ledger_account_id UUID           NOT NULL,
    side              VARCHAR(6)     NOT NULL,
    amount            NUMERIC(24, 8) NOT NULL,

    CONSTRAINT fk_journal_line_journal_entry
        FOREIGN KEY (journal_entry_id)
            REFERENCES journal_entry (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_journal_line_ledger_account
        FOREIGN KEY (ledger_account_id)
            REFERENCES ledger_account (id)
            ON DELETE RESTRICT,

    CONSTRAINT ck_journal_line_side
        CHECK (side IN ('DEBIT', 'CREDIT')),

    CONSTRAINT ck_journal_line_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_ledger_account_owner
    ON ledger_account (owner_type, owner_id);

CREATE INDEX idx_journal_entry_created_at
    ON journal_entry (created_at DESC, id);

CREATE INDEX idx_journal_line_journal_entry
    ON journal_line (journal_entry_id);

CREATE INDEX idx_journal_line_ledger_account
    ON journal_line (ledger_account_id, journal_entry_id);

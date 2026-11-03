CREATE TABLE payment_journal
(
    id               UUID PRIMARY KEY,
    payment_id       UUID NOT NULL,
    operation_type   VARCHAR(32) NOT NULL,
    journal_entry_id UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_payment_journal_payment
        FOREIGN KEY (payment_id)
            REFERENCES payment_intent (id),

    CONSTRAINT fk_payment_journal_journal
        FOREIGN KEY (journal_entry_id)
            REFERENCES journal_entry (id),

    CONSTRAINT uk_payment_journal_operation
        UNIQUE (payment_id, operation_type),

    CONSTRAINT uk_payment_journal_entry
        UNIQUE (journal_entry_id),

    CONSTRAINT ck_payment_journal_operation
        CHECK (operation_type IN ('CAPTURE'))
);
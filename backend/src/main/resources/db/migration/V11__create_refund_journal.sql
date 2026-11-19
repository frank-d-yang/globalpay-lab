CREATE TABLE refund_journal
(
    id               UUID PRIMARY KEY,
    refund_id        UUID NOT NULL,
    journal_entry_id UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_refund_journal_refund
        FOREIGN KEY (refund_id)
            REFERENCES refund (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_refund_journal_entry
        FOREIGN KEY (journal_entry_id)
            REFERENCES journal_entry (id)
            ON DELETE RESTRICT,

    CONSTRAINT uk_refund_journal_refund
        UNIQUE (refund_id),

    CONSTRAINT uk_refund_journal_entry
        UNIQUE (journal_entry_id)
);

package com.globalpay.lab.refund.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RefundJournal(
        UUID id,
        UUID refundId,
        UUID journalEntryId,
        Instant createdAt
) {
    public RefundJournal {
        Objects.requireNonNull(id);
        Objects.requireNonNull(refundId);
        Objects.requireNonNull(journalEntryId);
        Objects.requireNonNull(createdAt);
    }

    public static RefundJournal create(
            UUID refundId,
            UUID journalEntryId) {

        return new RefundJournal(
                UUID.randomUUID(),
                refundId,
                journalEntryId,
                Instant.now()
        );
    }
}


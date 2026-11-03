package com.globalpay.lab.payment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentJournal(
        UUID id,
        UUID paymentId,
        String operationType,
        UUID journalEntryId,
        Instant createdAt
) {

    public PaymentJournal {
        Objects.requireNonNull(id);
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(journalEntryId);
        Objects.requireNonNull(createdAt);

        if (!"CAPTURE".equals(operationType)) {
            throw new IllegalArgumentException(
                    "Unsupported payment operation: " + operationType
            );
        }
    }

    public static PaymentJournal capture(
            UUID paymentId,
            UUID journalEntryId
    ) {
        return new PaymentJournal(
                UUID.randomUUID(),
                paymentId,
                "CAPTURE",
                journalEntryId,
                Instant.now()
        );
    }
}


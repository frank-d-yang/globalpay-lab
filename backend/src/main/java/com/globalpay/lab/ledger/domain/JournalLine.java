package com.globalpay.lab.ledger.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class JournalLine {

    private final UUID id;
    private final UUID ledgerAccountId;
    private final EntrySide side;
    private final BigDecimal amount;

    public JournalLine(
            UUID id,
            UUID ledgerAccountId,
            EntrySide side,
            BigDecimal amount) {

        this.id = Objects.requireNonNull(id);
        this.ledgerAccountId = Objects.requireNonNull(ledgerAccountId);
        this.side = Objects.requireNonNull(side);
        this.amount = Objects.requireNonNull(amount);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getLedgerAccountId() {
        return ledgerAccountId;
    }

    public EntrySide getSide() {
        return side;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}

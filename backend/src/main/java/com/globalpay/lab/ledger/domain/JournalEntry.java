package com.globalpay.lab.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class JournalEntry {

    private final UUID id;
    private final Currency currency;
    private final Instant createdAt;
    private final List<JournalLine> lines;

    private JournalEntryStatus status;

    public JournalEntry(
            UUID id,
            Currency currency,
            Instant createdAt) {

        this.id = Objects.requireNonNull(id);
        this.currency = Objects.requireNonNull(currency);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.lines = new ArrayList<>();
        this.status = JournalEntryStatus.DRAFT;
    }

    public void addLine(JournalLine line) {
        if (status == JournalEntryStatus.POSTED) {
            throw new IllegalStateException(
                    "Cannot add lines to a posted journal entry"
            );
        }

        lines.add(Objects.requireNonNull(line));
    }

    public void post() {
        if (status == JournalEntryStatus.POSTED) {
            throw new IllegalStateException(
                    "Journal entry is already posted"
            );
        }

        if (lines.size() < 2) {
            throw new IllegalStateException(
                    "Journal entry must contain at least two lines"
            );
        }

        BigDecimal totalDebit = lines.stream()
                .filter(line -> line.getSide() == EntrySide.DEBIT)
                .map(JournalLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = lines.stream()
                .filter(line -> line.getSide() == EntrySide.CREDIT)
                .map(JournalLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalStateException(
                    "Journal entry is not balanced"
            );
        }

        status = JournalEntryStatus.POSTED;
    }

    public UUID getId() {
        return id;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public JournalEntryStatus getStatus() {
        return status;
    }

    public List<JournalLine> getLines() {
        return List.copyOf(lines);
    }
}
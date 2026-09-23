package com.globalpay.lab.ledger.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JournalEntryTest {

    @Test
    void shouldPostBalancedJournalEntry() {
        JournalEntry entry = new JournalEntry(
                UUID.randomUUID(),
                Currency.getInstance("AUD"),
                Instant.now()
        );

        JournalLine debitLine = new JournalLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntrySide.DEBIT,
                new BigDecimal("100.00")
        );

        JournalLine creditLine = new JournalLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntrySide.CREDIT,
                new BigDecimal("100.00")
        );

        entry.addLine(debitLine);
        entry.addLine(creditLine);

        entry.post();

        assertEquals(JournalEntryStatus.POSTED, entry.getStatus());
    }

    @Test
    void shouldRejectUnbalancedJournalEntry() {
        JournalEntry entry = new JournalEntry(
                UUID.randomUUID(),
                Currency.getInstance("AUD"),
                Instant.now()
        );

        JournalLine debitLine = new JournalLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntrySide.DEBIT,
                new BigDecimal("100.00")
        );

        JournalLine creditLine = new JournalLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntrySide.CREDIT,
                new BigDecimal("80.00")
        );

        entry.addLine(debitLine);
        entry.addLine(creditLine);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                entry::post
        );

        assertEquals(
                "Journal entry is not balanced",
                exception.getMessage()
        );

        assertEquals(
                JournalEntryStatus.DRAFT,
                entry.getStatus()
        );
    }

    @Test
    void shouldRejectJournalEntryWithLessThanTwoLines() {
        JournalEntry entry = new JournalEntry(
                UUID.randomUUID(),
                Currency.getInstance("AUD"),
                Instant.now()
        );

        JournalLine debitLine = new JournalLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntrySide.DEBIT,
                new BigDecimal("100.00")
        );

        entry.addLine(debitLine);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                entry::post
        );

        assertEquals(
                "Journal entry must contain at least two lines",
                exception.getMessage()
        );

        assertEquals(
                JournalEntryStatus.DRAFT,
                entry.getStatus()
        );
    }

    @Test
    void shouldRejectZeroOrNegativeAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JournalLine(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        EntrySide.DEBIT,
                        BigDecimal.ZERO
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new JournalLine(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        EntrySide.CREDIT,
                        new BigDecimal("-10.00")
                )
        );
    }

    @Test
    void shouldNotAllowAddingLinesAfterPosting() {
        JournalEntry entry = new JournalEntry(
                UUID.randomUUID(),
                Currency.getInstance("AUD"),
                Instant.now()
        );

        entry.addLine(new JournalLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntrySide.DEBIT,
                new BigDecimal("100.00")
        ));

        entry.addLine(new JournalLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntrySide.CREDIT,
                new BigDecimal("100.00")
        ));

        entry.post();

        JournalLine extraLine = new JournalLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntrySide.DEBIT,
                new BigDecimal("50.00")
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> entry.addLine(extraLine)
        );

        assertEquals(
                "Cannot add lines to a posted journal entry",
                exception.getMessage()
        );
    }
}

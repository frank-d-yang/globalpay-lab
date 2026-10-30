package com.globalpay.lab.ledger.application.query;

import com.globalpay.lab.ledger.domain.EntrySide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record JournalHistoryRecord(
        UUID journalId,
        UUID lineId,
        EntrySide side,
        BigDecimal amount,
        Instant postedAt
) {
}

package com.globalpay.lab.ledger.api.dto;

import com.globalpay.lab.ledger.application.query.JournalHistoryRecord;
import com.globalpay.lab.ledger.domain.AccountType;

import java.util.List;
import java.util.UUID;

public record JournalHistoryResponse(
        UUID accountId,
        String currency,
        AccountType accountType,
        List<JournalHistoryRecord> entries) {
}

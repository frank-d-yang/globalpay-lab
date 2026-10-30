package com.globalpay.lab.ledger.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LedgerBalanceResponse(
        UUID accountId,
        String currency,
        BigDecimal balance
) {}

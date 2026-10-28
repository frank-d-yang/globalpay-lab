package com.globalpay.lab.ledger.api.dto;

import com.globalpay.lab.ledger.domain.AccountStatus;
import com.globalpay.lab.ledger.domain.AccountType;
import com.globalpay.lab.ledger.domain.LedgerAccount;
import com.globalpay.lab.ledger.domain.OwnerType;

import java.time.Instant;
import java.util.UUID;

public record LedgerAccountResponse(
        UUID id,
        OwnerType ownerType,
        String ownerId,
        String currency,
        AccountType accountType,
        AccountStatus accountStatus,
        Instant createdAt
) {
    public static LedgerAccountResponse from(LedgerAccount account) {
        return new LedgerAccountResponse(
                account.getId(),
                account.getOwnerType(),
                account.getOwnerId(),
                account.getCurrency().getCurrencyCode(),
                account.getAccountType(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}

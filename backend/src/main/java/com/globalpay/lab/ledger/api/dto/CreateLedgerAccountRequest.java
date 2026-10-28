package com.globalpay.lab.ledger.api.dto;

import com.globalpay.lab.ledger.domain.AccountType;
import com.globalpay.lab.ledger.domain.OwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateLedgerAccountRequest(
        @NotNull
        OwnerType ownerType,

        @NotBlank
        String ownerId,

        @NotBlank
        String currency,

        @NotNull
        AccountType accountType
) {
}

package com.globalpay.lab.wallet.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(
        UUID ledgerAccountId,
        String currency,
        String status,
        BigDecimal recordedBalance
) {
}

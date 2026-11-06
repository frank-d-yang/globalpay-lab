package com.globalpay.lab.wallet.application;

import com.globalpay.lab.ledger.application.LedgerAccountService;
import com.globalpay.lab.ledger.domain.AccountType;
import com.globalpay.lab.ledger.domain.LedgerAccount;
import com.globalpay.lab.ledger.domain.OwnerType;
import com.globalpay.lab.ledger.infrastructure.LedgerAccountMapper;
import com.globalpay.lab.wallet.api.dto.WalletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class WalletService {

    @Autowired
    private LedgerAccountMapper ledgerAccountMapper;

    @Autowired
    private LedgerAccountService ledgerAccountService;

    public List<WalletResponse> listWallets(UUID merchantId) {
        return ledgerAccountMapper.findByOwnerId(merchantId.toString()).stream()
                .map(this::toResponse)
                .toList();
    }

    public WalletResponse getWallet(UUID merchantId, UUID ledgerAccountId) {
        LedgerAccount ledgerAccount = ledgerAccountMapper.findById(ledgerAccountId);
        // Hide whether another merchant's wallet ID exists.
        if (ledgerAccount == null
                || ledgerAccount.getOwnerType() != OwnerType.MERCHANT
                || ledgerAccount.getAccountType() != AccountType.LIABILITY
                || !ledgerAccount.getOwnerId().equals(merchantId.toString())
        ) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Wallet not found");
        }
        return toResponse(ledgerAccount);
    }

    private WalletResponse toResponse(LedgerAccount ledgerAccount) {
        return new WalletResponse(
                ledgerAccount.getId(),
                ledgerAccount.getCurrency().getCurrencyCode(),
                ledgerAccount.getStatus().name(),
                ledgerAccountService.getBalance(ledgerAccount.getId()));
    }
}

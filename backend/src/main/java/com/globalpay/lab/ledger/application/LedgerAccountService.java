package com.globalpay.lab.ledger.application;

import com.globalpay.lab.common.exception.BusinessException;
import com.globalpay.lab.common.api.ResultCode;
import com.globalpay.lab.ledger.application.query.JournalHistoryRecord;
import com.globalpay.lab.ledger.domain.AccountStatus;
import com.globalpay.lab.ledger.domain.AccountType;
import com.globalpay.lab.ledger.domain.LedgerAccount;
import com.globalpay.lab.ledger.domain.OwnerType;
import com.globalpay.lab.ledger.infrastructure.JournalLineMapper;
import com.globalpay.lab.ledger.infrastructure.LedgerAccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerAccountService {

    @Autowired
    private LedgerAccountMapper ledgerAccountMapper;

    @Autowired
    private JournalLineMapper journalLineMapper;

    @Transactional
    public LedgerAccount createAccount(OwnerType ownerType,
                                       String ownerId,
                                       Currency currency,
                                       AccountType accountType) {
        if (ownerType == null || currency == null || accountType == null) {
            throw new IllegalArgumentException("Required account fields are missing");
        }

        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Owner Id must not be empty");
        }

        LedgerAccount account = new LedgerAccount(
                UUID.randomUUID(),
                ownerType,
                ownerId,
                currency,
                accountType,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        ledgerAccountMapper.insert(account);

        return account;
    }

    public LedgerAccount getAccount(UUID id) {
        LedgerAccount ledgerAccount = ledgerAccountMapper.findById(id);

        if (ledgerAccount == null) {
            throw new BusinessException(ResultCode.ACCOUNT_NOT_FOUND);
        }

        return ledgerAccount;
    }

    public List<LedgerAccount> listAccounts() {
        return ledgerAccountMapper.findAll();
    }


    public BigDecimal getBalance(UUID accountId) {

        LedgerAccount account = getAccount(accountId);

        BigDecimal netAmount =
                ledgerAccountMapper.sumDebitMinusCredit(accountId);

        return switch (account.getAccountType()) {
            case ASSET, EXPENSE -> netAmount;
            case LIABILITY, REVENUE, EQUITY -> netAmount.negate();
        };
    }

    public List<JournalHistoryRecord> getJournalHistory(UUID accountId) {

        return journalLineMapper.findHistoryByAccountId(accountId);
    }
}

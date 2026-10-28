package com.globalpay.lab.ledger.infrastructure;

import com.globalpay.lab.ledger.domain.AccountStatus;
import com.globalpay.lab.ledger.domain.AccountType;
import com.globalpay.lab.ledger.domain.LedgerAccount;
import com.globalpay.lab.ledger.domain.OwnerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class LedgerAccountMapperTest {
    @Autowired
    private LedgerAccountMapper accountMapper;

    @Test
    void shouldInsertAndFindAccount() {

        UUID accountId = UUID.randomUUID();

        LedgerAccount account = new LedgerAccount(
                accountId,
                OwnerType.MERCHANT,
                "merchant-001",
                Currency.getInstance("AUD"),
                AccountType.LIABILITY,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        int inserted = accountMapper.insert(account);

        assertEquals(1, inserted);

        LedgerAccount saved = accountMapper.findById(accountId);

        assertNotNull(saved);
        assertEquals(accountId, saved.getId());
        assertEquals(OwnerType.MERCHANT, saved.getOwnerType());
        assertEquals("merchant-001", saved.getOwnerId());
        assertEquals("AUD", saved.getCurrency().getCurrencyCode());
        assertEquals(AccountType.LIABILITY, saved.getAccountType());
        assertEquals(AccountStatus.ACTIVE, saved.getStatus());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void shouldReturnNullWhenAccountNotFound() {
        UUID nonExistingId = UUID.randomUUID();

        LedgerAccount result = accountMapper.findById(nonExistingId);

        assertNull(result);
    }

    @Test
    void shouldFindAllAccounts() {
        UUID accountId = UUID.randomUUID();

        LedgerAccount account = new LedgerAccount(
                accountId,
                OwnerType.MERCHANT,
                "merchant-test",
                Currency.getInstance("AUD"),
                AccountType.LIABILITY,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        accountMapper.insert(account);

        List<LedgerAccount> accounts = accountMapper.findAll();

        assertNotNull(accounts);
        assertTrue(
                accounts.stream()
                        .anyMatch(a -> a.getId().equals(accountId))
        );
    }
}

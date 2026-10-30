package com.globalpay.lab.ledger.infrastructure;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.globalpay.lab.ledger.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class LedgerAccountMapperTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private LedgerAccountMapper accountMapper;

    @Autowired
    private JournalEntryMapper journalEntryMapper;

    @Autowired
    private JournalLineMapper journalLineMapper;

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

    @Test
    void shouldReturnZeroForEmptyAccount() {

        LedgerAccount account = createAccount(AccountType.ASSET);

        BigDecimal amount =
                accountMapper.sumDebitMinusCredit(account.getId());

        assertAmount("0", amount);
    }

    @Test
    void shouldIgnoreDraftJournalLines() {

        LedgerAccount asset = createAccount(AccountType.ASSET);
        LedgerAccount liability = createAccount(AccountType.LIABILITY);

        createJournal(
                asset.getId(), liability.getId(),
                EntrySide.DEBIT, "500.00", false
        );

        assertAmount(
                "0",
                accountMapper.sumDebitMinusCredit(asset.getId())
        );
    }

    @Test
    void shouldSumPostedDebitsAndCredits() {

        LedgerAccount asset = createAccount(AccountType.ASSET);
        LedgerAccount liability = createAccount(AccountType.LIABILITY);

        createJournal(
                asset.getId(), liability.getId(),
                EntrySide.DEBIT, "100.25", true
        );

        createJournal(
                asset.getId(), liability.getId(),
                EntrySide.DEBIT, "30.10", true
        );

        createJournal(
                asset.getId(), liability.getId(),
                EntrySide.CREDIT, "20.05", true
        );

        // 100.25 + 30.10 - 20.05 = 110.30
        assertAmount(
                "110.30",
                accountMapper.sumDebitMinusCredit(asset.getId())
        );
    }

    @Test
    void shouldIsolateAccountsAndPreserveDecimalPrecision() {

        LedgerAccount assetA = createAccount(AccountType.ASSET);
        LedgerAccount assetB = createAccount(AccountType.ASSET);
        LedgerAccount liability = createAccount(AccountType.LIABILITY);

        createJournal(
                assetA.getId(), liability.getId(),
                EntrySide.DEBIT, "100.12345678", true
        );

        createJournal(
                assetA.getId(), liability.getId(),
                EntrySide.CREDIT, "0.00000001", true
        );

        createJournal(
                assetB.getId(), liability.getId(),
                EntrySide.DEBIT, "999.99", true
        );

        assertAmount(
                "100.12345677",
                accountMapper.sumDebitMinusCredit(assetA.getId())
        );

        assertAmount(
                "999.99",
                accountMapper.sumDebitMinusCredit(assetB.getId())
        );
    }


    private LedgerAccount createAccount(AccountType type) {
        LedgerAccount account = new LedgerAccount(
                UUID.randomUUID(),
                OwnerType.PLATFORM,
                "test-" + UUID.randomUUID(),
                Currency.getInstance("AUD"),
                type,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        accountMapper.insert(account);

        return account;
    }

    private void createJournal(
            UUID accountId,
            UUID offsetAccountId,
            EntrySide side,
            String amount,
            boolean posted) {

        JournalEntry journal = new JournalEntry(
                UUID.randomUUID(),
                Currency.getInstance("AUD"),
                Instant.now()
        );

        EntrySide oppositeSide =
                side == EntrySide.DEBIT
                        ? EntrySide.CREDIT
                        : EntrySide.DEBIT;

        journal.addLine(new JournalLine(
                UUID.randomUUID(),
                accountId,
                side,
                new BigDecimal(amount)
        ));

        journal.addLine(new JournalLine(
                UUID.randomUUID(),
                offsetAccountId,
                oppositeSide,
                new BigDecimal(amount)
        ));

        journalEntryMapper.insert(journal);

        journalLineMapper.insertBatch(
                journal.getId(),
                journal.getLines()
        );

        if (posted) {
            journalEntryMapper.markPosted(journal.getId());
        }
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual)
        );
    }
}

package com.globalpay.lab.ledger.application;

import com.globalpay.lab.ledger.domain.*;
import com.globalpay.lab.ledger.infrastructure.JournalEntryMapper;
import com.globalpay.lab.ledger.infrastructure.LedgerAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class JournalServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private JournalService journalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LedgerAccountService ledgerAccountService;

    @Autowired
    private JournalEntryMapper journalEntryMapper;

    @Autowired
    private LedgerAccountMapper ledgerAccountMapper;

    @Test
    void shouldRollbackWhenJournalLinesInsertFails() {

        UUID journalId = UUID.randomUUID();

        JournalEntry journal = new JournalEntry(
                journalId,
                Currency.getInstance("AUD"),
                Instant.now()
        );

        // Both account IDs do not exist in database
        journal.addLine(new JournalLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntrySide.DEBIT,
                new BigDecimal("100.00")
        ));

        journal.addLine(new JournalLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntrySide.CREDIT,
                new BigDecimal("100.00")
        ));

        // JournalLine INSERT should violate foreign key
        assertThrows(
                RuntimeException.class,
                () -> journalService.createDraft(journal)
        );

        // JournalEntry must also be rolled back
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entry WHERE id = ?",
                Integer.class,
                journalId
        );

        assertEquals(0, count);
    }


    @Test
    void shouldPostBalancedJournal() {
        JournalEntry journal = createDraftJournal("100.00", "100.00");

        journalService.postJournal(journal.getId());

        JournalEntry saved =
                journalEntryMapper.findByIdForUpdate(journal.getId());

        assertEquals(JournalEntryStatus.POSTED, saved.getStatus());
    }

    @Test
    void shouldRejectUnbalancedJournal() {
        JournalEntry journal = createDraftJournal("100.00", "90.00");

        assertThrows(
                IllegalStateException.class,
                () -> journalService.postJournal(journal.getId())
        );

        JournalEntry saved =
                journalEntryMapper.findByIdForUpdate(journal.getId());

        assertEquals(JournalEntryStatus.DRAFT, saved.getStatus());
    }

    @Test
    void shouldRejectDuplicatePosting() {
        JournalEntry journal = createDraftJournal("100.00", "100.00");

        journalService.postJournal(journal.getId());

        assertThrows(
                IllegalStateException.class,
                () -> journalService.postJournal(journal.getId())
        );
    }


    private JournalEntry createDraftJournal(
            String debitAmount,
            String creditAmount) {

        Currency aud = Currency.getInstance("AUD");

        LedgerAccount asset = ledgerAccountService.createAccount(
                OwnerType.PLATFORM,
                "platform",
                aud,
                AccountType.ASSET
        );

        LedgerAccount liability = ledgerAccountService.createAccount(
                OwnerType.MERCHANT,
                "merchant-test",
                aud,
                AccountType.LIABILITY
        );

        JournalEntry journal = new JournalEntry(
                UUID.randomUUID(),
                aud,
                Instant.now()
        );

        journal.addLine(new JournalLine(
                UUID.randomUUID(),
                asset.getId(),
                EntrySide.DEBIT,
                new BigDecimal(debitAmount)
        ));

        journal.addLine(new JournalLine(
                UUID.randomUUID(),
                liability.getId(),
                EntrySide.CREDIT,
                new BigDecimal(creditAmount)
        ));

        journalService.createDraft(journal);

        return journal;
    }

    private JournalEntry createDraftJournal(
            UUID debitAccountId,
            UUID creditAccountId,
            String debitAmount,
            String creditAmount) {

        JournalEntry journal = new JournalEntry(
                UUID.randomUUID(),
                Currency.getInstance("AUD"),
                Instant.now()
        );

        journal.addLine(new JournalLine(
                UUID.randomUUID(),
                debitAccountId,
                EntrySide.DEBIT,
                new BigDecimal(debitAmount)
        ));

        journal.addLine(new JournalLine(
                UUID.randomUUID(),
                creditAccountId,
                EntrySide.CREDIT,
                new BigDecimal(creditAmount)
        ));

        journalService.createDraft(journal);

        return journal;
    }


    @Test
    void shouldRejectFrozenAccount() {

        Currency aud = Currency.getInstance("AUD");

        LedgerAccount frozenAccount = new LedgerAccount(
                UUID.randomUUID(),
                OwnerType.PLATFORM,
                "platform",
                aud,
                AccountType.ASSET,
                AccountStatus.FROZEN,
                Instant.now()
        );

        assertEquals(1, ledgerAccountMapper.insert(frozenAccount));

        LedgerAccount activeAccount = ledgerAccountService.createAccount(
                OwnerType.MERCHANT,
                "merchant-test",
                aud,
                AccountType.LIABILITY
        );

        JournalEntry journal = createDraftJournal(
                frozenAccount.getId(),
                activeAccount.getId(),
                "100.00",
                "100.00"
        );

        assertThrows(
                IllegalStateException.class,
                () -> journalService.postJournal(journal.getId())
        );

        JournalEntry saved =
                journalEntryMapper.findByIdForUpdate(journal.getId());

        assertEquals(JournalEntryStatus.DRAFT, saved.getStatus());
    }


    @Test
    void shouldRejectCurrencyMismatch() {

        LedgerAccount usdAccount = ledgerAccountService.createAccount(
                OwnerType.PLATFORM,
                "platform",
                Currency.getInstance("USD"),
                AccountType.ASSET
        );

        LedgerAccount audAccount = ledgerAccountService.createAccount(
                OwnerType.MERCHANT,
                "merchant-test",
                Currency.getInstance("AUD"),
                AccountType.LIABILITY
        );

        JournalEntry journal = createDraftJournal(
                usdAccount.getId(),
                audAccount.getId(),
                "100.00",
                "100.00"
        );

        assertThrows(
                IllegalStateException.class,
                () -> journalService.postJournal(journal.getId())
        );

        JournalEntry saved =
                journalEntryMapper.findByIdForUpdate(journal.getId());

        assertEquals(JournalEntryStatus.DRAFT, saved.getStatus());
    }




}


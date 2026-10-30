package com.globalpay.lab.ledger.infrastructure;

import com.globalpay.lab.ledger.application.query.JournalHistoryRecord;
import com.globalpay.lab.ledger.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class JournalLineMapperTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private JournalEntryMapper journalEntryMapper;

    @Autowired
    private JournalLineMapper journalLineMapper;

    @Autowired
    private LedgerAccountMapper ledgerAccountMapper;

    @Test
    void shouldInsertJournalLine() {

        UUID journalId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        // 1. 创建一个真实的 Ledger Account
        LedgerAccount account = new LedgerAccount(
                accountId,
                OwnerType.MERCHANT,
                "merchant-001",
                Currency.getInstance("AUD"),
                AccountType.LIABILITY,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        ledgerAccountMapper.insert(account);

        // 2. 创建 DRAFT Journal
        JournalEntry journal = new JournalEntry(
                journalId,
                Currency.getInstance("AUD"),
                Instant.now()
        );

        journalEntryMapper.insert(journal);

        // 3. 创建一条分录
        JournalLine line = new JournalLine(
                UUID.randomUUID(),
                accountId,
                EntrySide.CREDIT,
                new BigDecimal("100.00")
        );

        // 4. 执行真实数据库插入
        int rows = journalLineMapper.insertBatch(journalId, Collections.singletonList(line));

        // 5. 验证插入成功
        assertEquals(1, rows);
    }


    @Test
    void shouldFindJournalLinesByJournalEntryId() {

        Currency aud = Currency.getInstance("AUD");

        // 1. Create two accounts
        LedgerAccount debitAccount = new LedgerAccount(
                UUID.randomUUID(),
                OwnerType.PLATFORM,
                "platform",
                aud,
                AccountType.ASSET,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        LedgerAccount creditAccount = new LedgerAccount(
                UUID.randomUUID(),
                OwnerType.MERCHANT,
                "merchant-test",
                aud,
                AccountType.LIABILITY,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        ledgerAccountMapper.insert(debitAccount);
        ledgerAccountMapper.insert(creditAccount);

        // 2. Create draft journal
        JournalEntry journal = new JournalEntry(
                UUID.randomUUID(),
                aud,
                Instant.now()
        );

        journalEntryMapper.insert(journal);

        // 3. Create journal lines
        JournalLine debit = new JournalLine(
                UUID.randomUUID(),
                debitAccount.getId(),
                EntrySide.DEBIT,
                new BigDecimal("100.00")
        );

        JournalLine credit = new JournalLine(
                UUID.randomUUID(),
                creditAccount.getId(),
                EntrySide.CREDIT,
                new BigDecimal("100.00")
        );

        journalLineMapper.insertBatch(
                journal.getId(),
                List.of(debit, credit)
        );

        // 4. Query journal lines
        List<JournalLine> lines =
                journalLineMapper.findByJournalEntryId(
                        journal.getId()
                );

        // 5. Assertions
        assertEquals(2, lines.size());

        assertTrue(lines.stream().anyMatch(line ->
                line.getId().equals(debit.getId())
                        && line.getLedgerAccountId().equals(debitAccount.getId())
                        && line.getSide() == EntrySide.DEBIT
                        && line.getAmount().compareTo(
                        new BigDecimal("100.00")) == 0
        ));

        assertTrue(lines.stream().anyMatch(line ->
                line.getId().equals(credit.getId())
                        && line.getLedgerAccountId().equals(creditAccount.getId())
                        && line.getSide() == EntrySide.CREDIT
                        && line.getAmount().compareTo(
                        new BigDecimal("100.00")) == 0
        ));
    }


    @Test
    void shouldReturnEmptyHistoryForEmptyOrDraftAccount() {

        LedgerAccount account = createAccount();
        LedgerAccount offset = createAccount();

        assertTrue(journalLineMapper
                .findHistoryByAccountId(account.getId())
                .isEmpty());

        // Create DRAFT only
        createJournal(
                account.getId(),
                offset.getId(),
                EntrySide.DEBIT,
                "100.00",
                false
        );

        assertTrue(journalLineMapper
                .findHistoryByAccountId(account.getId())
                .isEmpty());
    }

    @Test
    void shouldReturnPostedHistoryWithCorrectFieldsAndOrder() {

        LedgerAccount account = createAccount();
        LedgerAccount offset = createAccount();

        JournalEntry first = createJournal(
                account.getId(), offset.getId(),
                EntrySide.DEBIT, "100.12345678", true
        );

        JournalEntry second = createJournal(
                account.getId(), offset.getId(),
                EntrySide.CREDIT, "20.00000001", true
        );

        List<JournalHistoryRecord> history =
                journalLineMapper.findHistoryByAccountId(account.getId());

        assertEquals(2, history.size());

        // Most recent posting first
        assertFalse(
                history.get(0).postedAt()
                        .isBefore(history.get(1).postedAt())
        );

        JournalHistoryRecord debit = history.stream()
                .filter(r -> r.journalId().equals(first.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(first.getLines().get(0).getId(), debit.lineId());
        assertEquals(EntrySide.DEBIT, debit.side());
        assertEquals(
                0,
                new BigDecimal("100.12345678")
                        .compareTo(debit.amount())
        );
        assertNotNull(debit.postedAt());

        JournalHistoryRecord credit = history.stream()
                .filter(r -> r.journalId().equals(second.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(second.getLines().get(0).getId(), credit.lineId());
        assertEquals(EntrySide.CREDIT, credit.side());
        assertEquals(
                0,
                new BigDecimal("20.00000001")
                        .compareTo(credit.amount())
        );
        assertNotNull(credit.postedAt());
    }

    @Test
    void shouldOnlyReturnHistoryForRequestedAccount() {

        LedgerAccount accountA = createAccount();
        LedgerAccount accountB = createAccount();
        LedgerAccount offset = createAccount();

        JournalEntry journalA = createJournal(
                accountA.getId(), offset.getId(),
                EntrySide.DEBIT, "100.00", true
        );

        createJournal(
                accountB.getId(), offset.getId(),
                EntrySide.DEBIT, "200.00", true
        );

        List<JournalHistoryRecord> history =
                journalLineMapper.findHistoryByAccountId(accountA.getId());

        assertEquals(1, history.size());
        assertEquals(journalA.getId(), history.get(0).journalId());
    }

    @Test
    void shouldReturnDistinctLinesFromSameJournal() {

        LedgerAccount account = createAccount();
        LedgerAccount offset = createAccount();

        JournalEntry journal = new JournalEntry(
                UUID.randomUUID(),
                Currency.getInstance("AUD"),
                Instant.now().minusSeconds(60)
        );

        JournalLine first = new JournalLine(
                UUID.randomUUID(), account.getId(),
                EntrySide.DEBIT, new BigDecimal("10.00")
        );

        JournalLine second = new JournalLine(
                UUID.randomUUID(), account.getId(),
                EntrySide.DEBIT, new BigDecimal("20.00")
        );

        JournalLine counterpart = new JournalLine(
                UUID.randomUUID(), offset.getId(),
                EntrySide.CREDIT, new BigDecimal("30.00")
        );

        journal.addLine(first);
        journal.addLine(second);
        journal.addLine(counterpart);

        journalEntryMapper.insert(journal);
        journalLineMapper.insertBatch(journal.getId(), journal.getLines());
        assertEquals(1, journalEntryMapper.markPosted(journal.getId()));

        List<JournalHistoryRecord> history =
                journalLineMapper.findHistoryByAccountId(account.getId());

        assertEquals(2, history.size());

        assertTrue(history.stream()
                .allMatch(r -> r.journalId().equals(journal.getId())));

        Set<UUID> lineIds = new HashSet<>();
        for (JournalHistoryRecord record : history) {
            lineIds.add(record.lineId());
        }

        assertEquals(Set.of(first.getId(), second.getId()), lineIds);
    }


    private LedgerAccount createAccount() {

        LedgerAccount account = new LedgerAccount(
                UUID.randomUUID(),
                OwnerType.PLATFORM,
                "test-" + UUID.randomUUID(),
                Currency.getInstance("AUD"),
                AccountType.ASSET,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        ledgerAccountMapper.insert(account);

        return account;
    }

    private JournalEntry createJournal(
            UUID accountId,
            UUID offsetAccountId,
            EntrySide side,
            String amount,
            boolean posted) {

        JournalEntry journal = new JournalEntry(
                UUID.randomUUID(),
                Currency.getInstance("AUD"),
                Instant.now().minusSeconds(60)
        );

        EntrySide opposite =
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
                opposite,
                new BigDecimal(amount)
        ));

        journalEntryMapper.insert(journal);

        journalLineMapper.insertBatch(
                journal.getId(),
                journal.getLines()
        );

        if (posted) {
            assertEquals(1, journalEntryMapper.markPosted(journal.getId()));
        }

        return journal;
    }



}


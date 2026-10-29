package com.globalpay.lab.ledger.infrastructure;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

}


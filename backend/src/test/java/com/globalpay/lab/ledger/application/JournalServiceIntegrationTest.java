package com.globalpay.lab.ledger.application;

import com.globalpay.lab.ledger.domain.*;
import com.globalpay.lab.ledger.infrastructure.JournalEntryMapper;
import com.globalpay.lab.ledger.infrastructure.LedgerAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.*;

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

    @Autowired
    private DataSource dataSource;

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


    @Test
    void shouldRejectUpdatingLineAfterPosting() {

        // 1. Create a balanced draft journal
        JournalEntry journal = createDraftJournal("100.00", "100.00");

        // 2. Post the journal
        journalService.postJournal(journal.getId());

        // 3. Attempt to modify a posted journal line
        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update("""
                    UPDATE journal_line
                    SET amount = ?
                    WHERE journal_entry_id = ?
                      AND side = 'DEBIT'
                    """,
                        new BigDecimal("90.00"),
                        journal.getId()
                )
        );

        // 4. Verify the original amount remains unchanged
        BigDecimal amount = jdbcTemplate.queryForObject("""
            SELECT amount
            FROM journal_line
            WHERE journal_entry_id = ?
              AND side = 'DEBIT'
            """,
                BigDecimal.class,
                journal.getId()
        );

        assertEquals(0, new BigDecimal("100.00").compareTo(amount));
    }


    @Test
    void shouldRejectDeletingLineAfterPosting() {

        // 1. Create and post a balanced journal
        JournalEntry journal = createDraftJournal("100.00", "100.00");

        journalService.postJournal(journal.getId());

        // 2. Attempt to delete a posted journal line
        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update("""
                    DELETE FROM journal_line
                    WHERE journal_entry_id = ?
                      AND side = 'DEBIT'
                    """,
                        journal.getId()
                )
        );

        // 3. Verify the journal lines still exist
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM journal_line
            WHERE journal_entry_id = ?
            """,
                Integer.class,
                journal.getId()
        );

        assertEquals(2, count);
    }


    @Test
    void shouldRejectInsertingLineAfterPosting() {

        // 1. Create and post a balanced journal
        JournalEntry journal = createDraftJournal("100.00", "100.00");

        journalService.postJournal(journal.getId());

        // 2. Reuse an existing ledger account
        UUID accountId = journal.getLines().get(0).getLedgerAccountId();

        // 3. Attempt to insert a new line into a posted journal
        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update("""
                    INSERT INTO journal_line (
                        id,
                        journal_entry_id,
                        ledger_account_id,
                        side,
                        amount
                    )
                    VALUES (?, ?, ?, 'DEBIT', ?)
                    """,
                        UUID.randomUUID(),
                        journal.getId(),
                        accountId,
                        new BigDecimal("50.00")
                )
        );

        // 4. Verify no additional line was inserted
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM journal_line
            WHERE journal_entry_id = ?
            """,
                Integer.class,
                journal.getId()
        );

        assertEquals(2, count);
    }


    @Test
    void shouldRejectUpdatingPostedJournalEntry() {

        // 1. Create and post a balanced journal
        JournalEntry journal = createDraftJournal("100.00", "100.00");

        journalService.postJournal(journal.getId());

        // 2. Attempt to revert POSTED to DRAFT
        assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update("""
                    UPDATE journal_entry
                    SET status = 'DRAFT',
                        posted_at = NULL
                    WHERE id = ?
                    """,
                        journal.getId()
                )
        );

        // 3. Verify the journal remains POSTED
        String status = jdbcTemplate.queryForObject("""
            SELECT status
            FROM journal_entry
            WHERE id = ?
            """,
                String.class,
                journal.getId()
        );

        assertEquals("POSTED", status);
    }

    @Test
    void shouldRejectDeletingPostedJournalEntry() {

        // 1. Create and post a balanced journal
        JournalEntry journal = createDraftJournal("100.00", "100.00");

        journalService.postJournal(journal.getId());

        // 2. Attempt to delete the posted journal
        DataAccessException exception = assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update("""
                    DELETE FROM journal_entry
                    WHERE id = ?
                    """,
                        journal.getId()
                )
        );

        // 3. Verify the rejection comes from our Trigger
        assertTrue(
                exception.getMostSpecificCause()
                        .getMessage()
                        .contains("Cannot modify or delete a posted journal")
        );

        // 4. Verify the journal still exists
        String status = jdbcTemplate.queryForObject("""
            SELECT status
            FROM journal_entry
            WHERE id = ?
            """,
                String.class,
                journal.getId()
        );

        assertEquals("POSTED", status);
    }


    @Test
    void shouldBlockConcurrentLineUpdateAndRejectAfterPosting() throws Exception {

        JournalEntry journal = createDraftJournal("100.00", "100.00");
        UUID journalId = journal.getId();

        CountDownLatch journalLocked = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CompletableFuture<Integer> threadBPid = new CompletableFuture<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Thread A: lock and post the journal
            Future<?> futureA = executor.submit(() -> {

                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);

                    try {
                        // Lock parent JournalEntry
                        try (PreparedStatement stmt = conn.prepareStatement("""
                            SELECT status
                            FROM journal_entry
                            WHERE id = ?
                            FOR UPDATE
                            """)) {

                            stmt.setObject(1, journalId);

                            try (ResultSet rs = stmt.executeQuery()) {
                                assertTrue(rs.next());
                                assertEquals("DRAFT", rs.getString("status"));
                            }
                        }

                        journalLocked.countDown();

                        // Hold the lock until the test allows commit
                        if (!allowCommit.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Commit wait timed out");
                        }

                        try (PreparedStatement stmt = conn.prepareStatement("""
                            UPDATE journal_entry
                            SET status = 'POSTED',
                                posted_at = clock_timestamp()
                            WHERE id = ?
                              AND status = 'DRAFT'
                            """)) {

                            stmt.setObject(1, journalId);
                            assertEquals(1, stmt.executeUpdate());
                        }

                        conn.commit();

                    } catch (Exception e) {
                        conn.rollback();
                        throw new RuntimeException(e);
                    }

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // Thread B: attempt to update a JournalLine
            Future<SQLException> futureB = executor.submit(() -> {

                if (!journalLocked.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Journal lock wait timed out");
                }

                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);

                    try {
                        // Record PostgreSQL connection PID
                        try (Statement stmt = conn.createStatement();
                             ResultSet rs = stmt.executeQuery(
                                     "SELECT pg_backend_pid()")) {

                            rs.next();
                            threadBPid.complete(rs.getInt(1));
                        }

                        try (PreparedStatement stmt = conn.prepareStatement("""
                            UPDATE journal_line
                            SET amount = 90
                            WHERE journal_entry_id = ?
                              AND side = 'DEBIT'
                            """)) {

                            stmt.setObject(1, journalId);
                            stmt.executeUpdate();

                            conn.commit();
                            return null;

                        } catch (SQLException e) {
                            conn.rollback();
                            return e;
                        }

                    } catch (Exception e) {
                        conn.rollback();
                        throw e;
                    }
                }
            });

            // Confirm B is actually blocked by a database lock
            int pid = threadBPid.get(10, TimeUnit.SECONDS);
            boolean blocked = false;

            for (int i = 0; i < 100; i++) {
                Boolean waiting = jdbcTemplate.queryForObject(
                        "SELECT cardinality(pg_blocking_pids(?)) > 0",
                        Boolean.class,
                        pid
                );

                if (Boolean.TRUE.equals(waiting)) {
                    blocked = true;
                    break;
                }

                Thread.sleep(50);
            }

            assertTrue(blocked, "Thread B should wait for the journal lock");

            // Let A commit and release the lock
            allowCommit.countDown();

            futureA.get(10, TimeUnit.SECONDS);

            // B resumes, sees POSTED, and gets rejected
            SQLException exception = futureB.get(10, TimeUnit.SECONDS);

            assertNotNull(exception);
            assertEquals("P0001", exception.getSQLState());
            assertTrue(exception.getMessage()
                    .contains("Cannot modify lines of a posted journal"));

            // Verify persisted state
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM journal_entry WHERE id = ?",
                    String.class,
                    journalId
            );

            BigDecimal amount = jdbcTemplate.queryForObject("""
                SELECT amount
                FROM journal_line
                WHERE journal_entry_id = ?
                  AND side = 'DEBIT'
                """,
                    BigDecimal.class,
                    journalId
            );

            assertEquals("POSTED", status);
            assertEquals(0, new BigDecimal("100.00").compareTo(amount));

        } finally {
            allowCommit.countDown();
            executor.shutdownNow();
        }
    }


}


package com.globalpay.lab.ledger.infrastructure;

import com.globalpay.lab.ledger.domain.JournalEntry;
import com.globalpay.lab.ledger.domain.JournalEntryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class JournalEntryMapperTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private JournalEntryMapper journalEntryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldInsertDraftJournalEntry() {

        JournalEntry journal = new JournalEntry(
                UUID.randomUUID(),
                Currency.getInstance("AUD"),
                Instant.now()
        );

        int rows = journalEntryMapper.insert(journal);

        assertEquals(1, rows);
    }

    @Test
    void shouldFindDraftJournalEntryByIdForUpdate() {

        JournalEntry journal = new JournalEntry(
                UUID.randomUUID(),
                Currency.getInstance("AUD"),
                Instant.parse("2026-01-01T10:00:00Z")
        );

        journalEntryMapper.insert(journal);

        JournalEntry saved =
                journalEntryMapper.findByIdForUpdate(journal.getId());

        assertNotNull(saved);
        assertEquals(journal.getId(), saved.getId());
        assertEquals(journal.getCurrency(), saved.getCurrency());
        assertEquals(journal.getCreatedAt(), saved.getCreatedAt());
        assertEquals(JournalEntryStatus.DRAFT, saved.getStatus());
    }

    @Test
    void shouldReturnNullWhenJournalNotFound() {
        UUID id = UUID.randomUUID();

        JournalEntry result =
                journalEntryMapper.findByIdForUpdate(id);

        assertNull(result);
    }

    @Test
    void shouldMapPostedStatusCorrectly() {
        JournalEntry journal = createJournal();

        journalEntryMapper.insert(journal);

        // Only prepare database state for the mapping test.
        jdbcTemplate.update("""
            UPDATE journal_entry
            SET status = 'POSTED',
                posted_at = clock_timestamp()
            WHERE id = ?
            """, journal.getId());

        JournalEntry result =
                journalEntryMapper.findByIdForUpdate(journal.getId());

        assertNotNull(result);
        assertEquals(JournalEntryStatus.POSTED, result.getStatus());
    }

    private JournalEntry createJournal() {
        return new JournalEntry(
                UUID.randomUUID(),
                Currency.getInstance("AUD"),
                Instant.parse("2026-01-01T10:00:00Z")
        );
    }

}


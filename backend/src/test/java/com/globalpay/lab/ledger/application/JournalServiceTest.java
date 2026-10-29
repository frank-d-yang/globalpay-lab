
package com.globalpay.lab.ledger.application;

import com.globalpay.lab.common.exception.BusinessException;
import com.globalpay.lab.ledger.domain.*;
import com.globalpay.lab.ledger.infrastructure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JournalServiceTest {

    @Mock
    private JournalEntryMapper journalEntryMapper;

    @Mock
    private JournalLineMapper journalLineMapper;

    @InjectMocks
    private JournalService journalService;

    @Test
    void shouldCreateDraftWithAllLines() {
        JournalEntry journal = createJournal();

        when(journalEntryMapper.insert(journal)).thenReturn(1);
        when(journalLineMapper.insertBatch(
                eq(journal.getId()), anyList()
        )).thenReturn(2);

        journalService.createDraft(journal);

        assertEquals(JournalEntryStatus.DRAFT, journal.getStatus());

        InOrder order = inOrder(
                journalEntryMapper, journalLineMapper
        );

        order.verify(journalEntryMapper).insert(journal);
        order.verify(journalLineMapper).insertBatch(
                journal.getId(), journal.getLines()
        );
    }

    @Test
    void shouldRejectPostedJournal() {
        JournalEntry journal = createJournal();
        journal.post();

        assertThrows(
                BusinessException.class,
                () -> journalService.createDraft(journal)
        );

        verifyNoInteractions(
                journalEntryMapper, journalLineMapper
        );
    }

    @Test
    void shouldFailWhenJournalInsertFails() {
        JournalEntry journal = createJournal();

        when(journalEntryMapper.insert(journal)).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> journalService.createDraft(journal)
        );

        verifyNoInteractions(journalLineMapper);
    }

    @Test
    void shouldFailWhenLineCountDoesNotMatch() {
        JournalEntry journal = createJournal();

        when(journalEntryMapper.insert(journal)).thenReturn(1);
        when(journalLineMapper.insertBatch(
                eq(journal.getId()), anyList()
        )).thenReturn(1);

        assertThrows(
                IllegalStateException.class,
                () -> journalService.createDraft(journal)
        );
    }

    private JournalEntry createJournal() {
        JournalEntry journal = new JournalEntry(
                UUID.randomUUID(),
                Currency.getInstance("AUD"),
                Instant.now()
        );

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

        return journal;
    }
}


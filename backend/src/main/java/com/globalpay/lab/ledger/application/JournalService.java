package com.globalpay.lab.ledger.application;

import com.globalpay.lab.common.api.ResultCode;
import com.globalpay.lab.common.exception.BusinessException;
import com.globalpay.lab.ledger.domain.*;
import com.globalpay.lab.ledger.infrastructure.JournalEntryMapper;
import com.globalpay.lab.ledger.infrastructure.JournalLineMapper;
import com.globalpay.lab.ledger.infrastructure.LedgerAccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class JournalService {

    @Autowired
    private JournalEntryMapper journalEntryMapper;

    @Autowired
    private JournalLineMapper journalLineMapper;

    @Autowired
    private LedgerAccountMapper ledgerAccountMapper;

    @Transactional
    public void createDraft(JournalEntry journal) {

        Objects.requireNonNull(journal);

        if (journal.getStatus() != JournalEntryStatus.DRAFT) {
            throw new BusinessException(
                    ResultCode.INVALID_JOURNAL_STATUS
            );
        }

        // Save journal
        if (journalEntryMapper.insert(journal) != 1) {
            throw new IllegalStateException(
                    "Failed to create journal"
            );
        }

        // Save Journal Lines
        List<JournalLine> lines = journal.getLines();

        if (!lines.isEmpty()) {
            int count = journalLineMapper.insertBatch(
                    journal.getId(), lines
            );

            if (count != lines.size()) {
                throw new IllegalStateException(
                        "Failed to save journal lines"
                );
            }
        }
    }


    @Transactional
    public void postJournal(UUID journalId) {

        // 查询并锁定 JournalEntry
        JournalEntry journal =
                journalEntryMapper.findByIdForUpdate(journalId);

        if (journal == null) {
            throw new IllegalStateException("Journal not found");
        }

        if (journal.getStatus() != JournalEntryStatus.DRAFT) {
            throw new IllegalStateException("Journal is already posted");
        }

        // 查询 JournalLines
        List<JournalLine> lines =
                journalLineMapper.findByJournalEntryId(journalId);

        // 恢复完整的 Journal 聚合
        for (JournalLine line : lines) {
            journal.addLine(line);
        }

        // 验证关联账户
        validateAccounts(journal);

        // 验证并改变领域对象状态
        journal.post();

        // 持久化过账状态
        int rows = journalEntryMapper.markPosted(journalId);

        if (rows != 1) {
            throw new IllegalStateException("Failed to post journal");
        }
    }

    private void validateAccounts(JournalEntry journal) {

        for (JournalLine line : journal.getLines()) {

            LedgerAccount account =
                    ledgerAccountMapper.findById(line.getLedgerAccountId());

            if (account == null) {
                throw new IllegalStateException(
                        "Ledger account not found: " + line.getLedgerAccountId()
                );
            }

            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new IllegalStateException(
                        "Ledger account is not active: " + account.getId()
                );
            }

            if (!account.getCurrency().equals(journal.getCurrency())) {
                throw new IllegalStateException(
                        "Ledger account currency does not match journal currency"
                );
            }
        }
    }

}

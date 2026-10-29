package com.globalpay.lab.ledger.infrastructure;

import com.globalpay.lab.ledger.domain.JournalLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface JournalLineMapper {
    int insertBatch(
            @Param("journalEntryId") UUID journalEntryId,
            @Param("lines") List<JournalLine> line
    );

    List<JournalLine> findByJournalEntryId(
            @Param("journalEntryId") UUID journalEntryId
    );
}

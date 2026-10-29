package com.globalpay.lab.ledger.infrastructure;

import com.globalpay.lab.ledger.domain.JournalEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface JournalEntryMapper {

    int insert(JournalEntry journalEntry);

    JournalEntry findByIdForUpdate(
            @Param("id") UUID id
    );

    int markPosted(@Param("id") UUID id);

}

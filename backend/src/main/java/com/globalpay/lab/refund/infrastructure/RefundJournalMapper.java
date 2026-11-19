package com.globalpay.lab.refund.infrastructure;

import com.globalpay.lab.refund.domain.RefundJournal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface RefundJournalMapper {

    int insert(RefundJournal mapping);

    RefundJournal findByRefundId(
            @Param("refundId") UUID refundId
    );
}


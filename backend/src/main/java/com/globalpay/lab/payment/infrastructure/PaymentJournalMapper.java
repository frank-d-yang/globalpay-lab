
package com.globalpay.lab.payment.infrastructure;

import com.globalpay.lab.payment.domain.PaymentJournal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface PaymentJournalMapper {

    int insert(PaymentJournal paymentJournal);

    PaymentJournal findByPaymentAndOperation(
            @Param("paymentId") UUID paymentId,
            @Param("operationType") String operationType
    );
}


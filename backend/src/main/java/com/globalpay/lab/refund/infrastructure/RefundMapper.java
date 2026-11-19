package com.globalpay.lab.refund.infrastructure;

import com.globalpay.lab.refund.domain.Refund;
import com.globalpay.lab.refund.domain.RefundStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface RefundMapper {

    int insertIfAbsent(Refund refund);

    Refund findByPaymentId(
            @Param("paymentId") UUID paymentId
    );

    Refund findByIdForUpdate(@Param("id") UUID id);

    int updateStatus(
            @Param("refund") Refund refund,
            @Param("expectedStatus") RefundStatus expectedStatus
    );

    Refund findByIdAndMerchantId(
            @Param("id") UUID refundId,
            @Param("merchantId") UUID merchantId
    );
}

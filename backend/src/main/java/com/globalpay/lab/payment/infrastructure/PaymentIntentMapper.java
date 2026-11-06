package com.globalpay.lab.payment.infrastructure;

import com.globalpay.lab.payment.domain.PaymentIntent;
import com.globalpay.lab.payment.domain.PaymentStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface PaymentIntentMapper {

    int insert(PaymentIntent payment);

    PaymentIntent findById(@Param("id") UUID id);

    int updateStatus(
            @Param("payment") PaymentIntent payment,
            @Param("expectedStatus") PaymentStatus expectedStatus
    );

    PaymentIntent findByMerchantAndIdempotencyKey(
            @Param("merchantId") UUID merchantId,
            @Param("idempotencyKey") String idempotencyKey
    );

    int insertIfAbsent(PaymentIntent payment);

    /**
     *  Finds a payment belonging to the specified merchant.
     *
     *  Although paymentId is unique, merchantId is included
     *  to enforce merchant-level data isolation.
     *
     * paymentId 已经是主键，仅通过 paymentId 就能唯一找到一笔 Payment。
     *
     * 但这里额外加上 merchantId，不是为了保证唯一性，而是为了保证数据访问权限（Authorization）。
     * 这是支付系统中非常重要的一个安全设计。
     *
     * 加上merchantId, 实现了商户之间的数据隔离（Tenant Isolation）
     *
     * @param paymentId
     * @param merchantId
     * @return
     */
    PaymentIntent findByIdAndMerchantId(@Param("id")UUID paymentId, @Param("merchantId")UUID merchantId);

    PaymentIntent findByIdForUpdate(@Param("id") UUID id);
}

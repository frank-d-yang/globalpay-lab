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
}

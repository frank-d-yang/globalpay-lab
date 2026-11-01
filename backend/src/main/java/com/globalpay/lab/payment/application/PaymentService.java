package com.globalpay.lab.payment.application;

import com.globalpay.lab.common.api.ResultCode;
import com.globalpay.lab.common.exception.BusinessException;
import com.globalpay.lab.payment.domain.PaymentIntent;
import com.globalpay.lab.payment.infrastructure.PaymentIntentMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

@Service
public class PaymentService {

    @Autowired
    private PaymentIntentMapper paymentMapper;

    public PaymentIntent createPayment(
            UUID merchantId,
            BigDecimal amount,
            Currency currency,
            String idempotencyKey) {

        if (merchantId == null ||
                currency == null ||
                amount == null ||
                amount.signum() <= 0 ||
                amount.scale() > 8 ||
                amount.precision() - amount.scale() > 16 ||
                idempotencyKey == null ||
                idempotencyKey.isBlank() ||
                idempotencyKey.trim().length() > 128) {

            throw new BusinessException(ResultCode.BAD_REQUEST);
        }

        String normalizedKey = idempotencyKey.trim();

        PaymentIntent payment = PaymentIntent.create(
                merchantId,
                amount,
                currency,
                normalizedKey
        );

        int inserted = paymentMapper.insertIfAbsent(payment);

        if (inserted == 1) {
            return payment;
        }

        PaymentIntent existing =
                paymentMapper.findByMerchantAndIdempotencyKey(
                        merchantId,
                        normalizedKey
                );

        if (existing == null) {
            throw new IllegalStateException(
                    "Existing payment not found after idempotency conflict"
            );
        }

        if (!payment.getRequestFingerprint()
                .equals(existing.getRequestFingerprint())) {

            throw new BusinessException(
                    ResultCode.PAYMENT_IDEMPOTENCY_CONFLICT
            );
        }

        return existing;
    }

    public PaymentIntent getPayment(UUID merchantId, UUID paymentId) {
        PaymentIntent payment =
                paymentMapper.findByIdAndMerchantId(
                        paymentId,
                        merchantId
                );

        if (payment == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        return payment;
    }
}


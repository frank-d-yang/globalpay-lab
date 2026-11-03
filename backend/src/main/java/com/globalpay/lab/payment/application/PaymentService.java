package com.globalpay.lab.payment.application;

import com.globalpay.lab.common.api.ResultCode;
import com.globalpay.lab.common.exception.BusinessException;
import com.globalpay.lab.payment.application.port.PaymentProvider;
import com.globalpay.lab.payment.application.port.ProviderOutcome;
import com.globalpay.lab.payment.application.port.ProviderResult;
import com.globalpay.lab.payment.domain.PaymentIntent;
import com.globalpay.lab.payment.domain.PaymentStatus;
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

    @Autowired
    private PaymentProvider paymentProvider;

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

    public PaymentIntent processPayment(
            UUID merchantId,
            UUID paymentId) {

        PaymentIntent payment = getPayment(merchantId, paymentId);

        // 1. Already finished
        if (payment.getStatus() == PaymentStatus.SUCCEEDED
                || payment.getStatus() == PaymentStatus.FAILED) {
            return payment;
        }

        // 2. Start processing
        boolean newlyStarted = false;

        if (payment.getStatus() == PaymentStatus.CREATED) {
            payment.startProcessing();

            int updated = paymentMapper.updateStatus(
                    payment,
                    PaymentStatus.CREATED
            );

            if (updated != 1) {
                throw new IllegalStateException(
                        "Payment status changed concurrently"
                );
            }

            newlyStarted = true;
        }

        // 3. Provider success already confirmed
        if (payment.getProviderReference() != null) {
            // TODO #25: Complete ledger posting
            return payment;
        }

        String operationId = "payment-" + payment.getId();

        ProviderResult result;

        if (newlyStarted) {
            // First attempt
            result = paymentProvider.process(payment, operationId);
        } else {
            // Recovery: check existing provider result
            result = paymentProvider.getResult(operationId);

            if (result.outcome() == ProviderOutcome.UNKNOWN) {
                // Safe retry using the SAME operation ID
                result = paymentProvider.process(payment, operationId);
            }
        }

        // 4. Handle provider outcome
        switch (result.outcome()) {

            case SUCCEEDED -> {
                payment.confirmProviderSuccess(
                        result.providerReference()
                );

                int updated = paymentMapper.updateStatus(
                        payment,
                        PaymentStatus.PROCESSING
                );

                if (updated != 1) {
                    throw new IllegalStateException(
                            "Failed to save provider success"
                    );
                }

                // TODO #25: Post journal to Ledger Core
            }

            case DECLINED -> {
                payment.fail(true);

                int updated = paymentMapper.updateStatus(
                        payment,
                        PaymentStatus.PROCESSING
                );

                if (updated != 1) {
                    throw new IllegalStateException(
                            "Failed to update declined payment"
                    );
                }
            }

            case UNKNOWN -> {
                // Keep PROCESSING.
                // Do not mark the payment FAILED.
            }
        }

        return payment;
    }

}


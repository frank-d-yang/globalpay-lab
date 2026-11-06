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

    @Autowired
    private PaymentLedgerService paymentLedgerService;

    /**
     * 代码逻辑：
     *
     * 先插入再查询，数据库对merchantId和幂等key加了唯一约束
     *
     * 为了处理并发的情况，先完成数据库的插入操作，并对插入结果进行判断
     * 如果inserted = 1，则说明成功插入
     * 如果inserted = 0，则说明之前有人创建了，（这里就很好的解决了并发的情形）
     *
     * 然后，因为数据库里已经有了，根据merchantId和幂等key把数据查出来，
     * 如果payment == null，抛异常，如果不为null，则比较参数指纹，
     * 如果参数指纹相同，返回已有payment，如果不同，说明新的请求有问题，因为幂等key相同，说明是同一创建请求，参数指纹应该相同。
     * 不同的话，需要抛出异常。
     *
     * 纠正：先查再插，这种无法解决并发情况下重复创建的问题
     * 比如A线程查到没有，A create,
     * B线程也查到没有，B也create，需要数据库的唯一约束避免B创建
     *
     * 然后B返回值是0，接着我们又得走一遍查询的判断，所以变成了查->插入->查询
     *
     * @param merchantId
     * @param amount
     * @param currency
     * @param idempotencyKey
     * @return
     */
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

            /**
             * 保证了只有一个线程能继续processPayment
             */
            if (updated != 1) {
                throw new IllegalStateException(
                        "Payment status changed concurrently"
                );
            }

            newlyStarted = true;
        }

        // 3. Provider success already confirmed
        if (payment.getProviderReference() != null) {
            return paymentLedgerService.completePayment(payment.getId());
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

                return paymentLedgerService.completePayment(
                        payment.getId()
                );
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


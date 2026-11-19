package com.globalpay.lab.refund.application;

import com.globalpay.lab.common.api.ResultCode;
import com.globalpay.lab.common.exception.BusinessException;
import com.globalpay.lab.mockpsp.application.MockPaymentService;
import com.globalpay.lab.mockpsp.application.MockRefundRequest;
import com.globalpay.lab.mockpsp.domain.ProviderOperation;
import com.globalpay.lab.payment.domain.PaymentIntent;
import com.globalpay.lab.payment.infrastructure.PaymentIntentMapper;
import com.globalpay.lab.refund.api.dto.RefundResponse;
import com.globalpay.lab.refund.domain.Refund;
import com.globalpay.lab.refund.domain.RefundStatus;
import com.globalpay.lab.refund.infrastructure.RefundMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RefundService {

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private PaymentIntentMapper paymentIntentMapper;

    @Autowired
    private MockPaymentService mockPaymentService;

    @Autowired
    private RefundLedgerService refundLedgerService;

    public RefundResponse refund(UUID merchantId, UUID paymentId) {
        PaymentIntent paymentIntent = paymentIntentMapper.findByIdAndMerchantId(paymentId, merchantId);
        if (paymentIntent == null) {
            throw new BusinessException(ResultCode.PAYMENT_NOT_FOUND);
        }

        Refund refund = Refund.create(paymentIntent);

        // Prevent duplicate refunds under concurrent requests.
        int inserted = refundMapper.insertIfAbsent(refund);

        if (inserted == 0) {
            refund = refundMapper.findByPaymentId(paymentId);

            if (refund == null) {
                throw new IllegalStateException(
                        "Existing refund not found after conflict"
                );
            }
        }

        if (refund.getStatus() == RefundStatus.SUCCEEDED
                || refund.getStatus() == RefundStatus.FAILED) {
            return RefundResponse.from(refund);
        }

        if (refund.getStatus() == RefundStatus.CREATED) {
            refund.startProcessing();

            int updated = refundMapper.updateStatus(
                    refund,
                    RefundStatus.CREATED
            );

            if (updated != 1) {
                throw new IllegalStateException(
                        "Refund status changed concurrently"
                );
            }
        }

        // Provider success was already confirmed.
        if (refund.getProviderReference() != null) {
            return RefundResponse.from(
                    refundLedgerService.completeRefund(refund.getId())
            );
        }

        MockRefundRequest request = new MockRefundRequest(
                refund.getId(),
                refund.getPaymentId(),
                refund.getAmount(),
                refund.getCurrency()
        );

        ProviderOperation operation =
                mockPaymentService.refund(request);

        switch (operation.getStatus()) {

            case SUCCEEDED -> {
                refund.confirmProviderSuccess(
                        operation.getProviderReference()
                );

                int updated = refundMapper.updateStatus(
                        refund,
                        RefundStatus.PROCESSING
                );

                if (updated != 1) {
                    throw new IllegalStateException(
                            "Failed to save provider refund result"
                    );
                }

                return RefundResponse.from(
                        refundLedgerService.completeRefund(refund.getId())
                );
            }

            case DECLINED -> {
                refund.fail(true);

                int updated = refundMapper.updateStatus(
                        refund,
                        RefundStatus.PROCESSING
                );

                if (updated != 1) {
                    throw new IllegalStateException(
                            "Failed to update declined refund"
                    );
                }
            }

            case PROCESSING -> {
                // Keep refund in PROCESSING.
            }
        }

        return RefundResponse.from(refund);
    }

    public RefundResponse getRefund(UUID merchantId, UUID refundId) {

        Refund refund = refundMapper.findByIdAndMerchantId(
                refundId, merchantId
        );

        if (refund == null) {
            throw new BusinessException(ResultCode.REFUND_NOT_FOUND);
        }

        return RefundResponse.from(refund);
    }
}

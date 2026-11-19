package com.globalpay.lab.refund.application;

import com.globalpay.lab.common.exception.BusinessException;
import com.globalpay.lab.mockpsp.application.MockPaymentService;
import com.globalpay.lab.mockpsp.application.MockRefundRequest;
import com.globalpay.lab.mockpsp.domain.ProviderOperation;
import com.globalpay.lab.mockpsp.domain.ProviderOperationStatus;
import com.globalpay.lab.payment.domain.PaymentIntent;
import com.globalpay.lab.payment.domain.PaymentStatus;
import com.globalpay.lab.payment.infrastructure.PaymentIntentMapper;
import com.globalpay.lab.refund.api.dto.RefundResponse;
import com.globalpay.lab.refund.domain.Refund;
import com.globalpay.lab.refund.domain.RefundStatus;
import com.globalpay.lab.refund.infrastructure.RefundMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private PaymentIntentMapper paymentIntentMapper;

    @Mock
    private RefundMapper refundMapper;

    @Mock
    private MockPaymentService mockPaymentService;

    @Mock
    private RefundLedgerService refundLedgerService;

    @InjectMocks
    private RefundService refundService;

    private UUID merchantId;
    private UUID paymentId;
    private PaymentIntent payment;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        paymentId = UUID.randomUUID();

        Instant now = Instant.now();

        payment = new PaymentIntent(
                paymentId,
                merchantId,
                new BigDecimal("100.00"),
                Currency.getInstance("AUD"),
                "payment-key-001",
                PaymentStatus.SUCCEEDED,
                "mock-payment-001",
                now,
                now
        );
    }

    @Test
    void shouldCompleteRefundSuccessfully() {
        mockPaymentLookup();

        AtomicReference<Refund> saved = mockNewRefund();

        mockProviderResult(
                ProviderOperationStatus.SUCCEEDED,
                "mock-refund-001"
        );

        when(refundLedgerService.completeRefund(any(UUID.class)))
                .thenAnswer(invocation -> {
                    Refund refund = saved.get();
                    refund.succeed(true, true);
                    return refund;
                });

        RefundResponse response =
                refundService.refund(merchantId, paymentId);

        assertEquals(paymentId, response.paymentId());
        assertEquals(0, new BigDecimal("100.00")
                .compareTo(response.amount()));
        assertEquals("AUD", response.currency());
        assertEquals(RefundStatus.SUCCEEDED, response.status());

        verify(refundMapper).updateStatus(
                any(Refund.class), eq(RefundStatus.CREATED)
        );

        verify(refundMapper).updateStatus(
                any(Refund.class), eq(RefundStatus.PROCESSING)
        );

        verify(mockPaymentService)
                .refund(any(MockRefundRequest.class));

        verify(refundLedgerService)
                .completeRefund(response.id());
    }

    @Test
    void shouldReturnExistingSucceededRefund() {
        mockPaymentLookup();

        Refund existing = Refund.create(payment);
        existing.startProcessing();
        existing.confirmProviderSuccess("mock-refund-001");
        existing.succeed(true, true);

        when(refundMapper.insertIfAbsent(any(Refund.class)))
                .thenReturn(0);

        when(refundMapper.findByPaymentId(paymentId))
                .thenReturn(existing);

        RefundResponse response =
                refundService.refund(merchantId, paymentId);

        assertEquals(existing.getId(), response.id());
        assertEquals(RefundStatus.SUCCEEDED, response.status());

        verifyNoInteractions(
                mockPaymentService,
                refundLedgerService
        );
    }

    @Test
    void shouldMarkRefundFailedWhenProviderDeclines() {
        mockPaymentLookup();
        mockNewRefund();

        mockProviderResult(
                ProviderOperationStatus.DECLINED,
                null
        );

        RefundResponse response =
                refundService.refund(merchantId, paymentId);

        assertEquals(RefundStatus.FAILED, response.status());

        verify(refundMapper).updateStatus(
                any(Refund.class), eq(RefundStatus.PROCESSING)
        );

        verifyNoInteractions(refundLedgerService);
    }

    @Test
    void shouldKeepProcessingWhenProviderResultIsUnknown() {
        mockPaymentLookup();
        mockNewRefund();

        mockProviderResult(
                ProviderOperationStatus.PROCESSING,
                null
        );

        RefundResponse response =
                refundService.refund(merchantId, paymentId);

        assertEquals(
                RefundStatus.PROCESSING,
                response.status()
        );

        verify(refundMapper, never()).updateStatus(
                any(Refund.class), eq(RefundStatus.PROCESSING)
        );

        verifyNoInteractions(refundLedgerService);
    }

    @Test
    void shouldResumeLedgerWithoutCallingProviderAgain() {
        mockPaymentLookup();

        Refund existing = Refund.create(payment);
        existing.startProcessing();
        existing.confirmProviderSuccess("mock-refund-001");

        when(refundMapper.insertIfAbsent(any(Refund.class)))
                .thenReturn(0);

        when(refundMapper.findByPaymentId(paymentId))
                .thenReturn(existing);

        when(refundLedgerService.completeRefund(existing.getId()))
                .thenAnswer(invocation -> {
                    existing.succeed(true, true);
                    return existing;
                });

        RefundResponse response =
                refundService.refund(merchantId, paymentId);

        assertEquals(RefundStatus.SUCCEEDED, response.status());

        verifyNoInteractions(mockPaymentService);

        verify(refundLedgerService)
                .completeRefund(existing.getId());
    }

    @Test
    void shouldRecoverAfterLedgerFailure() {
        mockPaymentLookup();

        AtomicReference<Refund> saved = new AtomicReference<>();
        AtomicInteger attempts = new AtomicInteger();

        when(refundMapper.insertIfAbsent(any(Refund.class)))
                .thenAnswer(invocation -> {
                    if (attempts.getAndIncrement() == 0) {
                        saved.set(invocation.getArgument(0));
                        return 1;
                    }
                    return 0;
                });

        when(refundMapper.findByPaymentId(paymentId))
                .thenAnswer(invocation -> saved.get());

        when(refundMapper.updateStatus(
                any(Refund.class), any(RefundStatus.class)
        )).thenReturn(1);

        mockProviderResult(
                ProviderOperationStatus.SUCCEEDED,
                "mock-refund-001"
        );

        when(refundLedgerService.completeRefund(any(UUID.class)))
                .thenAnswer(invocation -> {
                    if (attempts.get() == 1) {
                        throw new IllegalStateException(
                                "Simulated ledger failure"
                        );
                    }

                    Refund refund = saved.get();
                    refund.succeed(true, true);
                    return refund;
                });

        assertThrows(
                IllegalStateException.class,
                () -> refundService.refund(merchantId, paymentId)
        );

        assertEquals(
                RefundStatus.PROCESSING,
                saved.get().getStatus()
        );

        RefundResponse recovered =
                refundService.refund(merchantId, paymentId);

        assertEquals(RefundStatus.SUCCEEDED, recovered.status());

        // Provider must only be called once.
        verify(mockPaymentService, times(1))
                .refund(any(MockRefundRequest.class));

        verify(refundLedgerService, times(2))
                .completeRefund(saved.get().getId());
    }

    @Test
    void shouldRejectRefundWhenPaymentIsNotSucceeded() {
        Instant now = Instant.now();

        PaymentIntent processingPayment = new PaymentIntent(
                paymentId,
                merchantId,
                new BigDecimal("100.00"),
                Currency.getInstance("AUD"),
                "payment-key-002",
                PaymentStatus.PROCESSING,
                null,
                now,
                now
        );

        when(paymentIntentMapper.findByIdAndMerchantId(
                paymentId, merchantId
        )).thenReturn(processingPayment);

        assertThrows(
                IllegalStateException.class,
                () -> refundService.refund(merchantId, paymentId)
        );

        verifyNoInteractions(
                refundMapper,
                mockPaymentService,
                refundLedgerService
        );
    }

    @Test
    void shouldRejectRefundWhenPaymentNotFound() {
        when(paymentIntentMapper.findByIdAndMerchantId(
                paymentId, merchantId
        )).thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> refundService.refund(merchantId, paymentId)
        );

        verifyNoInteractions(
                refundMapper,
                mockPaymentService,
                refundLedgerService
        );
    }

    private void mockPaymentLookup() {
        when(paymentIntentMapper.findByIdAndMerchantId(
                paymentId, merchantId
        )).thenReturn(payment);
    }

    private AtomicReference<Refund> mockNewRefund() {
        AtomicReference<Refund> saved = new AtomicReference<>();

        when(refundMapper.insertIfAbsent(any(Refund.class)))
                .thenAnswer(invocation -> {
                    saved.set(invocation.getArgument(0));
                    return 1;
                });

        when(refundMapper.updateStatus(
                any(Refund.class), any(RefundStatus.class)
        )).thenReturn(1);

        return saved;
    }

    private void mockProviderResult(
            ProviderOperationStatus status,
            String reference) {

        ProviderOperation operation = mock(ProviderOperation.class);

        when(operation.getStatus()).thenReturn(status);

        if (status == ProviderOperationStatus.SUCCEEDED) {
            when(operation.getProviderReference())
                    .thenReturn(reference);
        }

        when(mockPaymentService.refund(any(MockRefundRequest.class)))
                .thenReturn(operation);
    }

    @Test
    void shouldGetRefundSuccessfully() {
        Refund refund = Refund.create(payment);

        when(refundMapper.findByIdAndMerchantId(
                refund.getId(), merchantId
        )).thenReturn(refund);

        RefundResponse response = refundService.getRefund(
                merchantId, refund.getId()
        );

        assertEquals(refund.getId(), response.id());
        assertEquals(paymentId, response.paymentId());
        assertEquals(RefundStatus.CREATED, response.status());
    }

    @Test
    void shouldRejectRefundNotBelongingToMerchant() {
        UUID refundId = UUID.randomUUID();

        when(refundMapper.findByIdAndMerchantId(
                refundId, merchantId
        )).thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> refundService.getRefund(merchantId, refundId)
        );

        verify(refundMapper).findByIdAndMerchantId(
                refundId, merchantId
        );
    }

}

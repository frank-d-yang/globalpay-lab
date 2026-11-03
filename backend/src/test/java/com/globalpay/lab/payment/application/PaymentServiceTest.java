package com.globalpay.lab.payment.application;

import com.globalpay.lab.common.api.ResultCode;
import com.globalpay.lab.common.exception.BusinessException;
import com.globalpay.lab.payment.application.port.PaymentProvider;
import com.globalpay.lab.payment.application.port.ProviderOutcome;
import com.globalpay.lab.payment.application.port.ProviderResult;
import com.globalpay.lab.payment.domain.PaymentIntent;
import com.globalpay.lab.payment.domain.PaymentStatus;
import com.globalpay.lab.payment.infrastructure.PaymentIntentMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentIntentMapper paymentMapper;

    @Mock
    private PaymentProvider paymentProvider;

    @InjectMocks
    private PaymentService paymentService;

    private final UUID merchantId = UUID.randomUUID();
    private final Currency aud = Currency.getInstance("AUD");
    private final String key = "order-001";

    private PaymentIntent createPayment() {
        return PaymentIntent.create(
                merchantId,
                new BigDecimal("100.00"),
                aud,
                UUID.randomUUID().toString()
        );
    }

    private void mockFindPayment(PaymentIntent payment) {
        when(paymentMapper.findByIdAndMerchantId(
                payment.getId(),
                merchantId
        )).thenReturn(payment);
    }


    @Test
    void shouldCreatePaymentSuccessfully() {

        when(paymentMapper.insertIfAbsent(any(PaymentIntent.class)))
                .thenReturn(1);

        PaymentIntent payment = paymentService.createPayment(
                merchantId,
                new BigDecimal("100.00"),
                aud,
                key
        );

        assertNotNull(payment);
        assertEquals(merchantId, payment.getMerchantId());
        assertEquals(0, new BigDecimal("100.00")
                .compareTo(payment.getAmount()));
        assertEquals(PaymentStatus.CREATED, payment.getStatus());

        verify(paymentMapper).insertIfAbsent(any(PaymentIntent.class));
        verify(paymentMapper, never())
                .findByMerchantAndIdempotencyKey(any(), any());
    }

    @Test
    void shouldReturnExistingPaymentForSameRequest() {

        PaymentIntent existing = PaymentIntent.create(
                merchantId,
                new BigDecimal("100.00"),
                aud,
                key
        );

        when(paymentMapper.insertIfAbsent(any(PaymentIntent.class)))
                .thenReturn(0);

        when(paymentMapper.findByMerchantAndIdempotencyKey(
                merchantId, key
        )).thenReturn(existing);

        PaymentIntent result = paymentService.createPayment(
                merchantId,
                new BigDecimal("100.0"),
                aud,
                key
        );

        assertSame(existing, result);
    }

    @Test
    void shouldRejectDifferentAmountWithSameKey() {

        PaymentIntent existing = PaymentIntent.create(
                merchantId,
                new BigDecimal("100.00"),
                aud,
                key
        );

        when(paymentMapper.insertIfAbsent(any(PaymentIntent.class)))
                .thenReturn(0);

        when(paymentMapper.findByMerchantAndIdempotencyKey(
                merchantId, key
        )).thenReturn(existing);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> paymentService.createPayment(
                        merchantId,
                        new BigDecimal("200.00"),
                        aud,
                        key
                )
        );

        assertEquals(
                ResultCode.PAYMENT_IDEMPOTENCY_CONFLICT,
                exception.getResultCode()
        );
    }

    // ===== Issue #24: Payment Processing =====

    @Test
    void shouldProcessPaymentSuccessfully() {

        PaymentIntent payment = createPayment();
        mockFindPayment(payment);

        when(paymentMapper.updateStatus(any(), any()))
                .thenReturn(1);

        when(paymentProvider.process(any(), anyString()))
                .thenReturn(new ProviderResult(
                        ProviderOutcome.SUCCEEDED,
                        "mock-ref-123"
                ));

        PaymentIntent result = paymentService.processPayment(
                merchantId,
                payment.getId()
        );

        assertEquals(PaymentStatus.PROCESSING, result.getStatus());
        assertEquals("mock-ref-123", result.getProviderReference());

        verify(paymentProvider).process(
                same(payment),
                eq("payment-" + payment.getId())
        );

        verify(paymentMapper).updateStatus(
                same(payment),
                eq(PaymentStatus.CREATED)
        );

        verify(paymentMapper).updateStatus(
                same(payment),
                eq(PaymentStatus.PROCESSING)
        );
    }

    @Test
    void shouldHandleProviderDecline() {

        PaymentIntent payment = createPayment();
        mockFindPayment(payment);

        when(paymentMapper.updateStatus(any(), any()))
                .thenReturn(1);

        when(paymentProvider.process(any(), anyString()))
                .thenReturn(new ProviderResult(
                        ProviderOutcome.DECLINED,
                        "mock-ref-declined"
                ));

        PaymentIntent result = paymentService.processPayment(
                merchantId,
                payment.getId()
        );

        assertEquals(PaymentStatus.FAILED, result.getStatus());

        verify(paymentMapper).updateStatus(
                same(payment),
                eq(PaymentStatus.PROCESSING)
        );
    }

    @Test
    void shouldKeepProcessingWhenProviderOutcomeUnknown() {

        PaymentIntent payment = createPayment();
        mockFindPayment(payment);

        when(paymentMapper.updateStatus(
                same(payment),
                eq(PaymentStatus.CREATED)
        )).thenReturn(1);

        when(paymentProvider.process(any(), anyString()))
                .thenReturn(new ProviderResult(
                        ProviderOutcome.UNKNOWN,
                        null
                ));

        PaymentIntent result = paymentService.processPayment(
                merchantId,
                payment.getId()
        );

        assertEquals(PaymentStatus.PROCESSING, result.getStatus());
        assertNull(result.getProviderReference());

        verify(paymentMapper, times(1))
                .updateStatus(any(), any());

        verify(paymentProvider).process(
                same(payment),
                eq("payment-" + payment.getId())
        );
    }

    @Test
    void shouldRecoverExistingProviderResult() {

        PaymentIntent payment = createPayment();
        payment.startProcessing();

        mockFindPayment(payment);

        when(paymentProvider.getResult(anyString()))
                .thenReturn(new ProviderResult(
                        ProviderOutcome.SUCCEEDED,
                        "mock-ref-456"
                ));

        when(paymentMapper.updateStatus(
                same(payment),
                eq(PaymentStatus.PROCESSING)
        )).thenReturn(1);

        PaymentIntent result = paymentService.processPayment(
                merchantId,
                payment.getId()
        );

        assertEquals(PaymentStatus.PROCESSING, result.getStatus());
        assertEquals("mock-ref-456", result.getProviderReference());

        verify(paymentProvider).getResult(
                "payment-" + payment.getId()
        );

        verify(paymentProvider, never())
                .process(any(), anyString());
    }

    @Test
    void shouldRetryUnknownProviderResultWithSameOperationId() {

        PaymentIntent payment = createPayment();
        payment.startProcessing();

        mockFindPayment(payment);

        String operationId = "payment-" + payment.getId();

        when(paymentProvider.getResult(operationId))
                .thenReturn(new ProviderResult(
                        ProviderOutcome.UNKNOWN,
                        null
                ));

        when(paymentProvider.process(
                same(payment),
                eq(operationId)
        )).thenReturn(new ProviderResult(
                ProviderOutcome.SUCCEEDED,
                "mock-ref-recovered"
        ));

        when(paymentMapper.updateStatus(
                same(payment),
                eq(PaymentStatus.PROCESSING)
        )).thenReturn(1);

        PaymentIntent result = paymentService.processPayment(
                merchantId,
                payment.getId()
        );

        assertEquals(
                "mock-ref-recovered",
                result.getProviderReference()
        );

        verify(paymentProvider).getResult(operationId);
        verify(paymentProvider).process(payment, operationId);
    }

    @Test
    void shouldNotCallProviderWhenStatusUpdateFails() {

        PaymentIntent payment = createPayment();
        mockFindPayment(payment);

        when(paymentMapper.updateStatus(
                same(payment),
                eq(PaymentStatus.CREATED)
        )).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.processPayment(
                        merchantId,
                        payment.getId()
                )
        );

        verifyNoInteractions(paymentProvider);
    }

    @Test
    void shouldNotProcessCompletedPayment() {

        PaymentIntent payment = createPayment();
        payment.startProcessing();
        payment.succeed(true, true);

        mockFindPayment(payment);

        PaymentIntent result = paymentService.processPayment(
                merchantId,
                payment.getId()
        );

        assertEquals(PaymentStatus.SUCCEEDED, result.getStatus());

        verifyNoInteractions(paymentProvider);
        verify(paymentMapper, never()).updateStatus(any(), any());
    }

    @Test
    void shouldKeepProcessingAfterProviderTimeout() {

        PaymentIntent payment = createPayment();
        mockFindPayment(payment);

        when(paymentMapper.updateStatus(
                same(payment),
                eq(PaymentStatus.CREATED)
        )).thenReturn(1);

        when(paymentProvider.process(any(), anyString()))
                .thenThrow(new IllegalStateException(
                        "Simulated provider timeout"
                ));

        assertThrows(
                IllegalStateException.class,
                () -> paymentService.processPayment(
                        merchantId,
                        payment.getId()
                )
        );

        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());

        verify(paymentMapper).updateStatus(
                same(payment),
                eq(PaymentStatus.CREATED)
        );
    }
}


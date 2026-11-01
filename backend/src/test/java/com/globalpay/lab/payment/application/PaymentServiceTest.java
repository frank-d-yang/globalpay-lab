package com.globalpay.lab.payment.application;

import com.globalpay.lab.common.api.ResultCode;
import com.globalpay.lab.common.exception.BusinessException;
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

    @InjectMocks
    private PaymentService paymentService;

    private final UUID merchantId = UUID.randomUUID();
    private final Currency aud = Currency.getInstance("AUD");
    private final String key = "order-001";

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
}


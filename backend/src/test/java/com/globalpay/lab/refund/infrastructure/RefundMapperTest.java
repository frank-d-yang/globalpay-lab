package com.globalpay.lab.refund.infrastructure;

import com.globalpay.lab.merchant.domain.Merchant;
import com.globalpay.lab.merchant.domain.MerchantStatus;
import com.globalpay.lab.merchant.infrastructure.MerchantMapper;
import com.globalpay.lab.payment.domain.PaymentIntent;
import com.globalpay.lab.payment.domain.PaymentStatus;
import com.globalpay.lab.payment.infrastructure.PaymentIntentMapper;
import com.globalpay.lab.refund.domain.Refund;
import com.globalpay.lab.refund.domain.RefundStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class RefundMapperTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private PaymentIntentMapper paymentIntentMapper;

    @Autowired
    private MerchantMapper merchantMapper;

    @Test
    void shouldInsertAndFindRefund() {
        PaymentIntent payment = createSucceededPayment();
        Refund refund = Refund.create(payment);

        int inserted = refundMapper.insertIfAbsent(refund);

        assertEquals(1, inserted);

        Refund saved =
                refundMapper.findByPaymentId(payment.getId());

        assertNotNull(saved);
        assertEquals(refund.getId(), saved.getId());
        assertEquals(payment.getId(), saved.getPaymentId());
        assertEquals(payment.getMerchantId(), saved.getMerchantId());
        assertEquals(RefundStatus.CREATED, saved.getStatus());
    }

    @Test
    void shouldPreventDuplicateRefund() {
        PaymentIntent payment = createSucceededPayment();

        Refund first = Refund.create(payment);
        Refund second = Refund.create(payment);

        assertEquals(1, refundMapper.insertIfAbsent(first));
        assertEquals(0, refundMapper.insertIfAbsent(second));

        Refund saved =
                refundMapper.findByPaymentId(payment.getId());

        assertNotNull(saved);
        assertEquals(first.getId(), saved.getId());
    }

    @Test
    void shouldMapRefundFieldsCorrectly() {
        PaymentIntent payment = createSucceededPayment();
        Refund refund = Refund.create(payment);

        refundMapper.insertIfAbsent(refund);

        Refund saved =
                refundMapper.findByPaymentId(payment.getId());

        assertNotNull(saved);

        assertEquals(
                0,
                refund.getAmount().compareTo(saved.getAmount())
        );

        assertEquals(refund.getCurrency(), saved.getCurrency());
        assertEquals(refund.getStatus(), saved.getStatus());
        assertNull(saved.getProviderReference());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    private PaymentIntent createSucceededPayment() {
        Merchant merchant = new Merchant(
                UUID.randomUUID(),
                "Refund Test Merchant",
                MerchantStatus.ACTIVE,
                Instant.now()
        );

        assertEquals(1, merchantMapper.insert(merchant));

        PaymentIntent payment = PaymentIntent.create(
                merchant.getId(),
                new BigDecimal("100.00"),
                Currency.getInstance("AUD"),
                UUID.randomUUID().toString()
        );

        assertEquals(1, paymentIntentMapper.insert(payment));

        payment.startProcessing();

        assertEquals(
                1,
                paymentIntentMapper.updateStatus(
                        payment,
                        PaymentStatus.CREATED
                )
        );

        payment.confirmProviderSuccess("mock-test-" + payment.getId());
        payment.succeed(true, true);

        assertEquals(
                1,
                paymentIntentMapper.updateStatus(
                        payment,
                        PaymentStatus.PROCESSING
                )
        );

        return payment;
    }
}
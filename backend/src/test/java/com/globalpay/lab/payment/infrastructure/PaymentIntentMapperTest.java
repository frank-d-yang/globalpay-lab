package com.globalpay.lab.payment.infrastructure;

import com.globalpay.lab.merchant.domain.Merchant;
import com.globalpay.lab.merchant.domain.MerchantStatus;
import com.globalpay.lab.merchant.infrastructure.MerchantMapper;
import com.globalpay.lab.payment.domain.PaymentIntent;
import com.globalpay.lab.payment.domain.PaymentStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
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
class PaymentIntentMapperTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private PaymentIntentMapper paymentIntentMapper;

    @Autowired
    private MerchantMapper merchantMapper;

    private PaymentIntent createPayment() {

        Merchant merchant = new Merchant(
                UUID.randomUUID(),
                "Frank Commerce",
                MerchantStatus.ACTIVE,
                Instant.now()
        );

        merchantMapper.insert(merchant);

        return PaymentIntent.create(
                merchant.getId(),
                new BigDecimal("700.00"),
                Currency.getInstance("AUD"),
                UUID.randomUUID().toString()
        );
    }

    @Test
    void shouldInsertAndFindPaymentIntent() {

        PaymentIntent payment = createPayment();

        assertEquals(1, paymentIntentMapper.insert(payment));

        PaymentIntent saved =
                paymentIntentMapper.findById(payment.getId());

        assertNotNull(saved);
        assertEquals(payment.getId(), saved.getId());
        assertEquals(payment.getMerchantId(), saved.getMerchantId());

        assertEquals(
                0,
                payment.getAmount().compareTo(saved.getAmount())
        );

        assertEquals(payment.getCurrency(), saved.getCurrency());
        assertEquals(
                payment.getIdempotencyKey(),
                saved.getIdempotencyKey()
        );
        assertEquals(PaymentStatus.CREATED, saved.getStatus());
        assertNull(saved.getProviderReference());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void shouldUpdatePaymentStatus() {

        PaymentIntent payment = createPayment();

        paymentIntentMapper.insert(payment);

        payment.startProcessing();

        int updated = paymentIntentMapper.updateStatus(
                payment,
                PaymentStatus.CREATED
        );

        assertEquals(1, updated);

        PaymentIntent saved =
                paymentIntentMapper.findById(payment.getId());

        assertEquals(
                PaymentStatus.PROCESSING,
                saved.getStatus()
        );

        // Stale status must not overwrite the current state.
        assertEquals(
                0,
                paymentIntentMapper.updateStatus(
                        payment,
                        PaymentStatus.CREATED
                )
        );
    }

    @Test
    void shouldRejectDuplicateIdempotencyKeyForSameMerchant() {

        PaymentIntent first = createPayment();

        paymentIntentMapper.insert(first);

        PaymentIntent duplicate = PaymentIntent.create(
                first.getMerchantId(),
                new BigDecimal("999.00"),
                Currency.getInstance("AUD"),
                first.getIdempotencyKey()
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> paymentIntentMapper.insert(duplicate)
        );
    }

    @Test
    void shouldFailAfterConfirmedProviderDecline() {

        PaymentIntent payment = createPayment();

        payment.startProcessing();
        payment.fail(true);

        assertEquals(
                PaymentStatus.FAILED,
                payment.getStatus()
        );

        assertThrows(
                IllegalStateException.class,
                payment::startProcessing
        );
    }
}


package com.globalpay.lab.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentIntentTest {

    private PaymentIntent newPayment() {
        return PaymentIntent.create(
                UUID.randomUUID(),
                new BigDecimal("700.00"),
                Currency.getInstance("AUD"),
                UUID.randomUUID().toString()
        );
    }

    @Test
    void shouldCreatePaymentIntent() {
        PaymentIntent payment = newPayment();

        assertNotNull(payment.getId());
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
        assertEquals(0,
                new BigDecimal("700.00")
                        .compareTo(payment.getAmount()));
    }

    @Test
    void shouldStartProcessing() {
        PaymentIntent payment = newPayment();

        payment.startProcessing();

        assertEquals(
                PaymentStatus.PROCESSING,
                payment.getStatus()
        );
    }

    @Test
    void shouldSucceedWhenProviderAndLedgerConfirmed() {
        PaymentIntent payment = newPayment();

        payment.startProcessing();
        payment.succeed(true, true);

        assertEquals(
                PaymentStatus.SUCCEEDED,
                payment.getStatus()
        );
    }

    @Test
    void shouldRemainProcessingWhenLedgerNotPosted() {
        PaymentIntent payment = newPayment();

        payment.startProcessing();

        assertThrows(
                IllegalStateException.class,
                () -> payment.succeed(true, false)
        );

        assertEquals(
                PaymentStatus.PROCESSING,
                payment.getStatus()
        );
    }

    @Test
    void shouldRejectUnconfirmedFailure() {
        PaymentIntent payment = newPayment();

        payment.startProcessing();

        assertThrows(
                IllegalStateException.class,
                () -> payment.fail(false)
        );

        assertEquals(
                PaymentStatus.PROCESSING,
                payment.getStatus()
        );
    }

    @Test
    void shouldRejectInvalidTransition() {
        PaymentIntent payment = newPayment();

        assertThrows(
                IllegalStateException.class,
                () -> payment.succeed(true, true)
        );

        assertEquals(
                PaymentStatus.CREATED,
                payment.getStatus()
        );
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PaymentIntent.create(
                        UUID.randomUUID(),
                        new BigDecimal("-100"),
                        Currency.getInstance("AUD"),
                        "test-key"
                )
        );
    }
}

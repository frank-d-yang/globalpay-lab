
package com.globalpay.lab.payment.application;

import com.globalpay.lab.ledger.application.JournalService;
import com.globalpay.lab.ledger.application.LedgerAccountService;
import com.globalpay.lab.ledger.domain.AccountType;
import com.globalpay.lab.ledger.domain.OwnerType;
import com.globalpay.lab.ledger.infrastructure.JournalEntryMapper;
import com.globalpay.lab.ledger.infrastructure.LedgerAccountMapper;
import com.globalpay.lab.payment.domain.*;
import com.globalpay.lab.payment.infrastructure.PaymentIntentMapper;
import com.globalpay.lab.payment.infrastructure.PaymentJournalMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentLedgerServiceTest {

    @Mock
    private PaymentIntentMapper paymentIntentMapper;

    @Mock
    private PaymentJournalMapper paymentJournalMapper;

    @Mock
    private LedgerAccountMapper ledgerAccountMapper;

    @Mock
    private JournalService journalService;

    @Mock
    private LedgerAccountService ledgerAccountService;

    @InjectMocks
    private PaymentLedgerService paymentLedgerService;


    @Test
    void shouldNotPostJournalForSucceededPayment() {
        PaymentIntent payment = createPayment(PaymentStatus.SUCCEEDED);

        when(paymentIntentMapper.findByIdForUpdate(payment.getId()))
                .thenReturn(payment);

        PaymentIntent result = paymentLedgerService.completePayment(
                payment.getId()
        );

        assertEquals(PaymentStatus.SUCCEEDED, result.getStatus());

        verifyNoInteractions(
                journalService,
                paymentJournalMapper,
                ledgerAccountMapper
        );
    }

    @Test
    void shouldRejectUnconfirmedPayment() {
        PaymentIntent payment = createPayment(PaymentStatus.PROCESSING);

        when(paymentIntentMapper.findByIdForUpdate(payment.getId()))
                .thenReturn(payment);

        assertThrows(IllegalStateException.class, () ->
                paymentLedgerService.completePayment(
                        payment.getId()
                )
        );

        verifyNoInteractions(journalService);
    }


    @Test
    void shouldRejectWhenLedgerAccountResolutionFails() {
        PaymentIntent payment =
                createPayment(PaymentStatus.PROCESSING);

        payment.confirmProviderSuccess("provider-123");

        when(paymentIntentMapper.findByIdForUpdate(payment.getId()))
                .thenReturn(payment);

        when(ledgerAccountService.getOrCreateAccount(
                eq(OwnerType.PLATFORM),
                eq("PROVIDER_CLEARING"),
                eq(Currency.getInstance("AUD")),
                eq(AccountType.ASSET)
        )).thenThrow(
                new IllegalStateException("Failed to resolve ledger account")
        );

        assertThrows(IllegalStateException.class, () ->
                paymentLedgerService.completePayment(payment.getId())
        );

        verifyNoInteractions(journalService);

        verify(paymentIntentMapper, never())
                .updateStatus(any(), any());
    }


    private PaymentIntent createPayment(PaymentStatus status) {
        Instant now = Instant.now();

        return new PaymentIntent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                Currency.getInstance("AUD"),
                UUID.randomUUID().toString(),
                status,
                null,
                now,
                now
        );
    }
}

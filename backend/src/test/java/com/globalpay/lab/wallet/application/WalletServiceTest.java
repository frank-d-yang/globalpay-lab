package com.globalpay.lab.wallet.application;

import com.globalpay.lab.ledger.application.LedgerAccountService;
import com.globalpay.lab.ledger.domain.*;
import com.globalpay.lab.ledger.infrastructure.LedgerAccountMapper;
import com.globalpay.lab.wallet.api.dto.WalletResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private LedgerAccountMapper ledgerAccountMapper;

    @Mock
    private LedgerAccountService ledgerAccountService;

    @InjectMocks
    private WalletService walletService;

    @Test
    void shouldListMerchantWallets() {
        UUID merchantId = UUID.randomUUID();

        LedgerAccount aud = createAccount(
                merchantId,
                "AUD"
        );

        LedgerAccount usd = createAccount(
                merchantId,
                "USD"
        );

        when(ledgerAccountMapper.findByOwnerId(
                merchantId.toString()
        )).thenReturn(List.of(aud, usd));

        when(ledgerAccountService.getBalance(aud.getId()))
                .thenReturn(new BigDecimal("300.00"));

        when(ledgerAccountService.getBalance(usd.getId()))
                .thenReturn(new BigDecimal("50.00"));

        List<WalletResponse> result =
                walletService.listWallets(merchantId);

        assertEquals(2, result.size());

        assertEquals(aud.getId(), result.get(0).ledgerAccountId());
        assertEquals("AUD", result.get(0).currency());
        assertEquals(
                0,
                new BigDecimal("300.00")
                        .compareTo(result.get(0).recordedBalance())
        );

        assertEquals(usd.getId(), result.get(1).ledgerAccountId());
        assertEquals("USD", result.get(1).currency());
        assertEquals(
                0,
                new BigDecimal("50.00")
                        .compareTo(result.get(1).recordedBalance())
        );
    }

    @Test
    void shouldReturnWalletById() {
        UUID merchantId = UUID.randomUUID();
        LedgerAccount account = createAccount(
                merchantId,
                "AUD"
        );

        when(ledgerAccountMapper.findById(account.getId()))
                .thenReturn(account);

        when(ledgerAccountService.getBalance(account.getId()))
                .thenReturn(new BigDecimal("100.00"));

        WalletResponse result = walletService.getWallet(
                merchantId,
                account.getId()
        );

        assertEquals(account.getId(), result.ledgerAccountId());
        assertEquals("AUD", result.currency());
        assertEquals("ACTIVE", result.status());

        assertEquals(
                0,
                new BigDecimal("100.00")
                        .compareTo(result.recordedBalance())
        );
    }

    @Test
    void shouldRejectOtherMerchantWallet() {
        UUID merchantA = UUID.randomUUID();
        UUID merchantB = UUID.randomUUID();

        LedgerAccount account = createAccount(
                merchantB,
                "AUD"
        );

        when(ledgerAccountMapper.findById(account.getId()))
                .thenReturn(account);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> walletService.getWallet(
                        merchantA,
                        account.getId()
                )
        );

        assertEquals(HttpStatus.NOT_FOUND,
                exception.getStatusCode());

        verifyNoInteractions(ledgerAccountService);
    }

    @Test
    void shouldRejectNonMerchantPayableAccount() {
        UUID merchantId = UUID.randomUUID();

        LedgerAccount account = new LedgerAccount(
                UUID.randomUUID(),
                OwnerType.MERCHANT,
                merchantId.toString(),
                Currency.getInstance("AUD"),
                AccountType.ASSET,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        when(ledgerAccountMapper.findById(account.getId()))
                .thenReturn(account);

        assertThrows(
                ResponseStatusException.class,
                () -> walletService.getWallet(
                        merchantId,
                        account.getId()
                )
        );

        verifyNoInteractions(ledgerAccountService);
    }

    @Test
    void shouldRejectMissingWallet() {
        UUID merchantId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        when(ledgerAccountMapper.findById(accountId))
                .thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> walletService.getWallet(
                        merchantId,
                        accountId
                )
        );

        assertEquals(HttpStatus.NOT_FOUND,
                exception.getStatusCode());

        verifyNoInteractions(ledgerAccountService);
    }

    private LedgerAccount createAccount(
            UUID merchantId,
            String currency) {

        return new LedgerAccount(
                UUID.randomUUID(),
                OwnerType.MERCHANT,
                merchantId.toString(),
                Currency.getInstance(currency),
                AccountType.LIABILITY,
                AccountStatus.ACTIVE,
                Instant.now()
        );
    }
}


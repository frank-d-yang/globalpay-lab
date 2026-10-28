package com.globalpay.lab.ledger.application;

import com.globalpay.lab.common.exception.BusinessException;
import com.globalpay.lab.ledger.domain.AccountStatus;
import com.globalpay.lab.ledger.domain.AccountType;
import com.globalpay.lab.ledger.domain.LedgerAccount;
import com.globalpay.lab.ledger.domain.OwnerType;
import com.globalpay.lab.ledger.infrastructure.LedgerAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Currency;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LedgerAccountServiceTest {

    @Mock
    private LedgerAccountMapper accountMapper;

    @InjectMocks
    private LedgerAccountService accountService;

    @Test
    public void shouldCreateAccount() {
        when(accountMapper.insert(any())).thenReturn(1);

        LedgerAccount account = accountService.createAccount(
                OwnerType.MERCHANT,
                "merchant-001",
                Currency.getInstance("AUD"),
                AccountType.LIABILITY
        );

        assertNotNull(account.getId());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals("AUD", account.getCurrency().getCurrencyCode());

        verify(accountMapper).insert(any(LedgerAccount.class));
    }

    @Test
    void shouldRejectBlankOwnerId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount(
                        OwnerType.MERCHANT,
                        " ",
                        Currency.getInstance("AUD"),
                        AccountType.LIABILITY
                )
        );

        verifyNoInteractions(accountMapper);
    }

    @Test
    void shouldThrowWhenAccountNotFound() {
        UUID id = UUID.randomUUID();

        when(accountMapper.findById(id)).thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> accountService.getAccount(id)
        );
    }
}

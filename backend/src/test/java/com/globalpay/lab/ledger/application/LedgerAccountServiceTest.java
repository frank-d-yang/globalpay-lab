package com.globalpay.lab.ledger.application;

import com.globalpay.lab.common.exception.BusinessException;
import com.globalpay.lab.ledger.application.query.JournalHistoryRecord;
import com.globalpay.lab.ledger.domain.*;
import com.globalpay.lab.ledger.infrastructure.JournalLineMapper;
import com.globalpay.lab.ledger.infrastructure.LedgerAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LedgerAccountServiceTest {

    @Mock
    private LedgerAccountMapper accountMapper;

    @Mock
    private JournalLineMapper journalLineMapper;

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


    @ParameterizedTest
    @CsvSource({
            "ASSET, 100.25, 100.25",
            "EXPENSE, 100.25, 100.25",
            "LIABILITY, -100.25, 100.25",
            "REVENUE, -100.25, 100.25",
            "EQUITY, -100.25, 100.25"
    })
    void shouldCalculateBalanceByAccountType(
            AccountType type,
            String netAmount,
            String expectedBalance) {

        UUID accountId = UUID.randomUUID();

        LedgerAccount account = new LedgerAccount(
                accountId,
                OwnerType.PLATFORM,
                "platform",
                Currency.getInstance("AUD"),
                type,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        // Mock database responses
        when(accountMapper.findById(accountId))
                .thenReturn(account);

        when(accountMapper.sumDebitMinusCredit(accountId))
                .thenReturn(new BigDecimal(netAmount));

        // Execute business logic
        BigDecimal balance = accountService.getBalance(accountId);

        // Verify calculated balance
        assertEquals(
                0,
                new BigDecimal(expectedBalance).compareTo(balance)
        );

        verify(accountMapper).findById(accountId);
        verify(accountMapper).sumDebitMinusCredit(accountId);
    }


    @Test
    void shouldReturnZeroBalanceForEmptyAccount() {

        UUID accountId = UUID.randomUUID();

        LedgerAccount account = new LedgerAccount(
                accountId,
                OwnerType.PLATFORM,
                "platform",
                Currency.getInstance("AUD"),
                AccountType.ASSET,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        when(accountMapper.findById(accountId))
                .thenReturn(account);

        when(accountMapper.sumDebitMinusCredit(accountId))
                .thenReturn(BigDecimal.ZERO);

        BigDecimal balance = accountService.getBalance(accountId);

        assertEquals(0, BigDecimal.ZERO.compareTo(balance));
    }

    @Test
    void shouldRejectBalanceQueryWhenAccountNotFound() {

        UUID accountId = UUID.randomUUID();

        when(accountMapper.findById(accountId))
                .thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> accountService.getBalance(accountId)
        );

        verify(accountMapper).findById(accountId);

        verify(accountMapper, never())
                .sumDebitMinusCredit(accountId);
    }

    @Test
    void shouldReturnJournalHistory() {

        UUID accountId = UUID.randomUUID();

        JournalHistoryRecord record = new JournalHistoryRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                EntrySide.CREDIT,
                new BigDecimal("100.25"),
                Instant.now()
        );

        List<JournalHistoryRecord> expected = List.of(record);

        when(journalLineMapper.findHistoryByAccountId(accountId))
                .thenReturn(expected);

        List<JournalHistoryRecord> actual =
                accountService.getJournalHistory(accountId);

        assertEquals(expected, actual);

        verify(journalLineMapper).findHistoryByAccountId(accountId);
    }

    @Test
    void shouldReturnEmptyJournalHistory() {

        UUID accountId = UUID.randomUUID();

        when(journalLineMapper.findHistoryByAccountId(accountId))
                .thenReturn(List.of());

        List<JournalHistoryRecord> result =
                accountService.getJournalHistory(accountId);

        assertTrue(result.isEmpty());

        verify(journalLineMapper).findHistoryByAccountId(accountId);
    }
}

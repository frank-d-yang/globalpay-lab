package com.globalpay.lab.payment.application;

import com.globalpay.lab.ledger.application.LedgerAccountService;
import com.globalpay.lab.ledger.domain.*;
import com.globalpay.lab.merchant.application.MerchantService;
import com.globalpay.lab.merchant.domain.Merchant;
import com.globalpay.lab.payment.domain.*;
import com.globalpay.lab.payment.infrastructure.PaymentIntentMapper;
import com.globalpay.lab.payment.infrastructure.PaymentJournalMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.mock.http.server.reactive.MockServerHttpRequest.post;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

@SpringBootTest
@Testcontainers
class PaymentLedgerServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private PaymentLedgerService paymentLedgerService;

    @Autowired
    private PaymentIntentMapper paymentIntentMapper;

    @Autowired
    private PaymentJournalMapper paymentJournalMapper;

    @Autowired
    private LedgerAccountService ledgerAccountService;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPostJournalAndCompletePayment() {
        Fixture f = createFixture();

        UUID clearingId = getClearingAccountId();
        UUID payableId = getPayableAccountId(f.merchantId());

        BigDecimal clearingBefore =
                ledgerAccountService.getBalance(clearingId);

        BigDecimal payableBefore =
                ledgerAccountService.getBalance(payableId);

        PaymentIntent result =
                paymentLedgerService.completePayment(f.paymentId());

        assertEquals(
                PaymentStatus.SUCCEEDED,
                result.getStatus()
        );

        PaymentJournal mapping =
                paymentJournalMapper.findByPaymentAndOperation(
                        f.paymentId(), "CAPTURE"
                );

        assertNotNull(mapping);

        String journalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM journal_entry WHERE id = ?",
                String.class,
                mapping.journalEntryId()
        );

        assertEquals("POSTED", journalStatus);

        Integer lineCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM journal_line
                WHERE journal_entry_id = ?
                """,
                Integer.class,
                mapping.journalEntryId()
        );

        assertEquals(2, lineCount);

        // Verify debit and credit entries
        assertJournalLine(
                mapping.journalEntryId(),
                clearingId,
                "DEBIT",
                "100.00"
        );

        assertJournalLine(
                mapping.journalEntryId(),
                payableId,
                "CREDIT",
                "100.00"
        );

        // Verify balance changes
        assertBalanceChange(clearingId, clearingBefore, "100.00");
        assertBalanceChange(payableId, payableBefore, "100.00");
    }

    @Test
    void shouldNotPostDuplicateJournal() {
        Fixture f = createFixture();

        UUID clearingId = getClearingAccountId();
        UUID payableId = getPayableAccountId(f.merchantId());

        BigDecimal clearingBefore =
                ledgerAccountService.getBalance(clearingId);

        BigDecimal payableBefore =
                ledgerAccountService.getBalance(payableId);

        // First execution
        paymentLedgerService.completePayment(f.paymentId());

        PaymentJournal first =
                paymentJournalMapper.findByPaymentAndOperation(
                        f.paymentId(), "CAPTURE"
                );

        assertNotNull(first);

        // Retry
        PaymentIntent second =
                paymentLedgerService.completePayment(f.paymentId());

        PaymentJournal afterRetry =
                paymentJournalMapper.findByPaymentAndOperation(
                        f.paymentId(), "CAPTURE"
                );

        assertEquals(
                PaymentStatus.SUCCEEDED,
                second.getStatus()
        );

        assertNotNull(afterRetry);

        assertEquals(
                first.journalEntryId(),
                afterRetry.journalEntryId()
        );

        // Balance should increase only once
        assertBalanceChange(clearingId, clearingBefore, "100.00");
        assertBalanceChange(payableId, payableBefore, "100.00");

        Long mappingCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM payment_journal
                WHERE payment_id = ?
                  AND operation_type = 'CAPTURE'
                """,
                Long.class,
                f.paymentId()
        );

        assertEquals(1L, mappingCount);
    }

    @Test
    void shouldRollbackJournalAndPaymentOnTransactionFailure() {
        Fixture f = createFixture();

        UUID clearingId = getClearingAccountId();
        UUID payableId = getPayableAccountId(f.merchantId());

        BigDecimal clearingBefore =
                ledgerAccountService.getBalance(clearingId);

        BigDecimal payableBefore =
                ledgerAccountService.getBalance(payableId);

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () ->
                transaction.execute(status -> {

                    PaymentIntent result =
                            paymentLedgerService.completePayment(
                                    f.paymentId()
                            );

                    assertEquals(
                            PaymentStatus.SUCCEEDED,
                            result.getStatus()
                    );

                    // Simulate failure before commit
                    throw new IllegalStateException(
                            "Simulated failure"
                    );
                })
        );

        PaymentIntent payment =
                paymentIntentMapper.findById(f.paymentId());

        assertEquals(
                PaymentStatus.PROCESSING,
                payment.getStatus()
        );

        PaymentJournal mapping =
                paymentJournalMapper.findByPaymentAndOperation(
                        f.paymentId(), "CAPTURE"
                );

        assertNull(mapping);

        // No balance changes after rollback
        assertBalanceChange(clearingId, clearingBefore, "0");
        assertBalanceChange(payableId, payableBefore, "0");
    }

    @Test
    void shouldRejectPaymentWithoutProviderConfirmation() {
        Merchant merchant =
                merchantService.createMerchant("Unconfirmed Merchant");

        PaymentIntent payment = PaymentIntent.create(
                merchant.getId(),
                new BigDecimal("100.00"),
                Currency.getInstance("AUD"),
                UUID.randomUUID().toString()
        );

        assertEquals(1, paymentIntentMapper.insert(payment));

        assertThrows(IllegalStateException.class, () ->
                paymentLedgerService.completePayment(
                        payment.getId()
                )
        );

        assertNull(
                paymentJournalMapper.findByPaymentAndOperation(
                        payment.getId(), "CAPTURE"
                )
        );

        PaymentIntent stored =
                paymentIntentMapper.findById(payment.getId());

        assertEquals(
                PaymentStatus.CREATED,
                stored.getStatus()
        );
    }

    private Fixture createFixture() {
        Merchant merchant =
                merchantService.createMerchant("Test Merchant");

        Currency aud = Currency.getInstance("AUD");

        PaymentIntent payment = PaymentIntent.create(
                merchant.getId(),
                new BigDecimal("100.00"),
                aud,
                UUID.randomUUID().toString()
        );

        assertEquals(1, paymentIntentMapper.insert(payment));

        payment.startProcessing();

        assertEquals(1, paymentIntentMapper.updateStatus(
                payment, PaymentStatus.CREATED
        ));

        payment.confirmProviderSuccess(
                "provider-" + UUID.randomUUID()
        );

        assertEquals(1, paymentIntentMapper.updateStatus(
                payment, PaymentStatus.PROCESSING
        ));

        return new Fixture(
                payment.getId(),
                merchant.getId()
        );
    }

    private UUID getClearingAccountId() {
        return ledgerAccountService.getOrCreateAccount(
                OwnerType.PLATFORM,
                "PROVIDER_CLEARING",
                Currency.getInstance("AUD"),
                AccountType.ASSET
        ).getId();
    }

    private UUID getPayableAccountId(UUID merchantId) {
        return ledgerAccountService.getOrCreateAccount(
                OwnerType.MERCHANT,
                merchantId.toString(),
                Currency.getInstance("AUD"),
                AccountType.LIABILITY
        ).getId();
    }

    private void assertBalanceChange(
            UUID accountId,
            BigDecimal before,
            String expectedChange) {

        BigDecimal after =
                ledgerAccountService.getBalance(accountId);

        BigDecimal actualChange = after.subtract(before);

        assertEquals(
                0,
                new BigDecimal(expectedChange).compareTo(actualChange)
        );
    }

    private void assertJournalLine(
            UUID journalId,
            UUID accountId,
            String side,
            String expectedAmount) {

        BigDecimal amount = jdbcTemplate.queryForObject(
                """
                SELECT amount
                FROM journal_line
                WHERE journal_entry_id = ?
                  AND ledger_account_id = ?
                  AND side = ?
                """,
                BigDecimal.class,
                journalId,
                accountId,
                side
        );

        assertNotNull(amount);

        assertEquals(
                0,
                new BigDecimal(expectedAmount).compareTo(amount)
        );
    }

    private record Fixture(
            UUID paymentId,
            UUID merchantId
    ) {
    }


    @Test
    void shouldPostJournalOnlyOnceForConcurrentRequests()
            throws Exception {

        Fixture f = createFixture();

        UUID payableId = getPayableAccountId(f.merchantId());

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<PaymentIntent> task = () -> {
            ready.countDown();

            if (!start.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start timed out");
            }

            return paymentLedgerService.completePayment(f.paymentId());
        };

        try {
            Future<PaymentIntent> first = executor.submit(task);
            Future<PaymentIntent> second = executor.submit(task);

            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();

            PaymentIntent resultA = first.get(30, TimeUnit.SECONDS);
            PaymentIntent resultB = second.get(30, TimeUnit.SECONDS);

            // Both calls must succeed
            assertEquals(PaymentStatus.SUCCEEDED, resultA.getStatus());
            assertEquals(PaymentStatus.SUCCEEDED, resultB.getStatus());

            // Only one capture journal mapping
            Long count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM payment_journal
                    WHERE payment_id = ?
                      AND operation_type = 'CAPTURE'
                    """,
                    Long.class,
                    f.paymentId()
            );

            assertEquals(1L, count);

            // Merchant must receive only AUD 100
            BigDecimal balance =
                    ledgerAccountService.getBalance(payableId);

            assertEquals(
                    0,
                    new BigDecimal("100.00").compareTo(balance)
            );

            // The journal must be POSTED
            PaymentJournal mapping =
                    paymentJournalMapper.findByPaymentAndOperation(
                            f.paymentId(), "CAPTURE"
                    );

            assertNotNull(mapping);

            String journalStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM journal_entry WHERE id = ?",
                    String.class,
                    mapping.journalEntryId()
            );

            assertEquals("POSTED", journalStatus);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }




}

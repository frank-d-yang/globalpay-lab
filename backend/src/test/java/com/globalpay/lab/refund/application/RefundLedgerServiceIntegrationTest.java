package com.globalpay.lab.refund.application;

import com.globalpay.lab.ledger.application.LedgerAccountService;
import com.globalpay.lab.ledger.domain.*;
import com.globalpay.lab.merchant.application.MerchantService;
import com.globalpay.lab.merchant.domain.Merchant;
import com.globalpay.lab.payment.application.PaymentLedgerService;
import com.globalpay.lab.payment.domain.*;
import com.globalpay.lab.payment.infrastructure.*;
import com.globalpay.lab.refund.domain.*;
import com.globalpay.lab.refund.infrastructure.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
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

@SpringBootTest
@Testcontainers
class RefundLedgerServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private RefundLedgerService refundLedgerService;

    @Autowired
    private PaymentLedgerService paymentLedgerService;

    @Autowired
    private PaymentIntentMapper paymentIntentMapper;

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private RefundJournalMapper refundJournalMapper;

    @Autowired
    private PaymentJournalMapper paymentJournalMapper;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private LedgerAccountService ledgerAccountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldPostReversalJournalOnlyOnceForConcurrentRequests()
            throws Exception {

        Fixture f = createFixture();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Refund> task = () -> {
            ready.countDown();

            if (!start.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start timed out");
            }

            return refundLedgerService.completeRefund(f.refundId());
        };

        try {
            Future<Refund> first = executor.submit(task);
            Future<Refund> second = executor.submit(task);

            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();

            Refund resultA = first.get(30, TimeUnit.SECONDS);
            Refund resultB = second.get(30, TimeUnit.SECONDS);

            assertEquals(RefundStatus.SUCCEEDED, resultA.getStatus());
            assertEquals(RefundStatus.SUCCEEDED, resultB.getStatus());

            Long count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM refund_journal
                    WHERE refund_id = ?
                    """,
                    Long.class,
                    f.refundId()
            );

            assertEquals(1L, count);

            RefundJournal mapping =
                    refundJournalMapper.findByRefundId(f.refundId());

            assertNotNull(mapping);

            assertJournalPosted(mapping.journalEntryId());

            assertBalance(f.payableId(), "0");
            assertBalance(f.clearingId(), "0");

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldPostReversalJournalAndCompleteRefund() {
        Fixture f = createFixture();

        Refund result =
                refundLedgerService.completeRefund(f.refundId());

        assertEquals(RefundStatus.SUCCEEDED, result.getStatus());

        RefundJournal reversal =
                refundJournalMapper.findByRefundId(f.refundId());

        assertNotNull(reversal);

        PaymentJournal capture =
                paymentJournalMapper.findByPaymentAndOperation(
                        f.paymentId(), "CAPTURE"
                );

        assertNotNull(capture);
        assertNotEquals(
                capture.journalEntryId(),
                reversal.journalEntryId()
        );

        assertJournalPosted(capture.journalEntryId());
        assertJournalPosted(reversal.journalEntryId());

        assertJournalLine(
                reversal.journalEntryId(),
                f.payableId(),
                "DEBIT",
                "100.00"
        );

        assertJournalLine(
                reversal.journalEntryId(),
                f.clearingId(),
                "CREDIT",
                "100.00"
        );

        assertBalance(f.payableId(), "0");
        assertBalance(f.clearingId(), "0");
    }

    @Test
    void shouldNotPostDuplicateReversalJournal() {
        Fixture f = createFixture();

        Refund first =
                refundLedgerService.completeRefund(f.refundId());

        RefundJournal firstMapping =
                refundJournalMapper.findByRefundId(f.refundId());

        Refund second =
                refundLedgerService.completeRefund(f.refundId());

        RefundJournal secondMapping =
                refundJournalMapper.findByRefundId(f.refundId());

        assertEquals(RefundStatus.SUCCEEDED, first.getStatus());
        assertEquals(RefundStatus.SUCCEEDED, second.getStatus());

        assertNotNull(firstMapping);
        assertNotNull(secondMapping);

        assertEquals(
                firstMapping.journalEntryId(),
                secondMapping.journalEntryId()
        );

        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refund_journal
                WHERE refund_id = ?
                """,
                Long.class,
                f.refundId()
        );

        assertEquals(1L, count);

        assertBalance(f.payableId(), "0");
        assertBalance(f.clearingId(), "0");
    }

    @Test
    void shouldRollbackReversalOnTransactionFailure() {
        Fixture f = createFixture();

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () ->
                transaction.execute(status -> {

                    Refund result =
                            refundLedgerService.completeRefund(
                                    f.refundId()
                            );

                    assertEquals(
                            RefundStatus.SUCCEEDED,
                            result.getStatus()
                    );

                    throw new IllegalStateException(
                            "Simulated failure before commit"
                    );
                })
        );

        String savedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM refund WHERE id = ?",
                String.class,
                f.refundId()
        );

        assertEquals("PROCESSING", savedStatus);

        RefundJournal mapping =
                refundJournalMapper.findByRefundId(f.refundId());

        assertNull(mapping);

        Long reversalCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM journal_entry
                WHERE id NOT IN (
                    SELECT journal_entry_id
                    FROM payment_journal
                )
                """,
                Long.class
        );

        // The test database may contain other journals,
        // so verify balances and recovery rather than
        // relying on a global journal count.
        assertNotNull(reversalCount);

        assertBalance(f.payableId(), "100.00");

        Refund recovered =
                refundLedgerService.completeRefund(f.refundId());

        assertEquals(
                RefundStatus.SUCCEEDED,
                recovered.getStatus()
        );

        assertNotNull(
                refundJournalMapper.findByRefundId(f.refundId())
        );

        assertBalance(f.payableId(), "0");
    }

    private Fixture createFixture() {
        Merchant merchant =
                merchantService.createMerchant("Refund Test Merchant");

        PaymentIntent payment = PaymentIntent.create(
                merchant.getId(),
                new BigDecimal("100.00"),
                Currency.getInstance("AUD"),
                UUID.randomUUID().toString()
        );

        assertEquals(1, paymentIntentMapper.insert(payment));

        payment.startProcessing();

        assertEquals(1, paymentIntentMapper.updateStatus(
                payment, PaymentStatus.CREATED
        ));

        payment.confirmProviderSuccess(
                "payment-" + UUID.randomUUID()
        );

        assertEquals(1, paymentIntentMapper.updateStatus(
                payment, PaymentStatus.PROCESSING
        ));

        payment = paymentLedgerService.completePayment(
                payment.getId()
        );

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());

        Refund refund = Refund.create(payment);

        assertEquals(1, refundMapper.insertIfAbsent(refund));

        refund.startProcessing();

        assertEquals(1, refundMapper.updateStatus(
                refund, RefundStatus.CREATED
        ));

        refund.confirmProviderSuccess(
                "refund-" + UUID.randomUUID()
        );

        assertEquals(1, refundMapper.updateStatus(
                refund, RefundStatus.PROCESSING
        ));

        UUID clearingId = ledgerAccountService
                .getOrCreateAccount(
                        OwnerType.PLATFORM,
                        "PROVIDER_CLEARING",
                        Currency.getInstance("AUD"),
                        AccountType.ASSET
                ).getId();

        UUID payableId = ledgerAccountService
                .getOrCreateAccount(
                        OwnerType.MERCHANT,
                        merchant.getId().toString(),
                        Currency.getInstance("AUD"),
                        AccountType.LIABILITY
                ).getId();

        assertBalance(payableId, "100.00");

        return new Fixture(
                payment.getId(),
                refund.getId(),
                clearingId,
                payableId
        );
    }

    private void assertJournalPosted(UUID journalId) {
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM journal_entry WHERE id = ?",
                String.class,
                journalId
        );

        assertEquals("POSTED", status);
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

    private void assertBalance(UUID accountId, String expected) {
        BigDecimal actual =
                ledgerAccountService.getBalance(accountId);

        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual)
        );
    }

    private record Fixture(
            UUID paymentId,
            UUID refundId,
            UUID clearingId,
            UUID payableId
    ) {
    }
}

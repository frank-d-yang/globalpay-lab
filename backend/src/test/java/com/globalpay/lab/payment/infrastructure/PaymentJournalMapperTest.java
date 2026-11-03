package com.globalpay.lab.payment.infrastructure;

import com.globalpay.lab.ledger.domain.JournalEntry;
import com.globalpay.lab.ledger.infrastructure.JournalEntryMapper;
import com.globalpay.lab.merchant.application.MerchantService;
import com.globalpay.lab.merchant.domain.Merchant;
import com.globalpay.lab.payment.domain.PaymentIntent;
import com.globalpay.lab.payment.domain.PaymentJournal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
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
class PaymentJournalMapperIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private PaymentIntentMapper paymentIntentMapper;

    @Autowired
    private JournalEntryMapper journalEntryMapper;

    @Autowired
    private PaymentJournalMapper paymentJournalMapper;

    @Test
    void shouldInsertAndFindPaymentJournal() {
        UUID paymentId = createPayment();
        UUID journalId = createJournal();

        PaymentJournal mapping =
                PaymentJournal.capture(paymentId, journalId);

        assertEquals(1, paymentJournalMapper.insert(mapping));

        PaymentJournal found =
                paymentJournalMapper.findByPaymentAndOperation(
                        paymentId, "CAPTURE");

        assertNotNull(found);
        assertEquals(mapping.id(), found.id());
        assertEquals(paymentId, found.paymentId());
        assertEquals("CAPTURE", found.operationType());
        assertEquals(journalId, found.journalEntryId());
        assertNotNull(found.createdAt());
    }

    @Test
    void shouldRejectDuplicatePaymentOperation() {
        UUID paymentId = createPayment();

        PaymentJournal first = PaymentJournal.capture(
                paymentId, createJournal());

        PaymentJournal second = PaymentJournal.capture(
                paymentId, createJournal());

        paymentJournalMapper.insert(first);

        assertThrows(
                DataAccessException.class,
                () -> paymentJournalMapper.insert(second)
        );
    }

    @Test
    void shouldRejectReusingSameJournal() {
        UUID journalId = createJournal();

        PaymentJournal first = PaymentJournal.capture(
                createPayment(), journalId);

        PaymentJournal second = PaymentJournal.capture(
                createPayment(), journalId);

        paymentJournalMapper.insert(first);

        assertThrows(
                DataAccessException.class,
                () -> paymentJournalMapper.insert(second)
        );
    }

    @Test
    void shouldReturnNullWhenMappingDoesNotExist() {
        PaymentJournal found =
                paymentJournalMapper.findByPaymentAndOperation(
                        UUID.randomUUID(), "CAPTURE");

        assertNull(found);
    }

    private UUID createPayment() {
        Merchant merchant =
                merchantService.createMerchant("Test Merchant");

        PaymentIntent payment = PaymentIntent.create(
                merchant.getId(),
                new BigDecimal("100.00"),
                Currency.getInstance("AUD"),
                UUID.randomUUID().toString()
        );

        assertEquals(1, paymentIntentMapper.insert(payment));

        return payment.getId();
    }

    private UUID createJournal() {
        JournalEntry journal = new JournalEntry(
                UUID.randomUUID(),
                Currency.getInstance("AUD"),
                Instant.now()
        );

        assertEquals(1, journalEntryMapper.insert(journal));

        return journal.getId();
    }
}

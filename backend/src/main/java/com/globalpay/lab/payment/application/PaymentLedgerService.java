package com.globalpay.lab.payment.application;

import com.globalpay.lab.ledger.application.JournalService;
import com.globalpay.lab.ledger.application.LedgerAccountService;
import com.globalpay.lab.ledger.domain.*;
import com.globalpay.lab.ledger.infrastructure.JournalEntryMapper;
import com.globalpay.lab.payment.domain.*;
import com.globalpay.lab.payment.infrastructure.PaymentIntentMapper;
import com.globalpay.lab.payment.infrastructure.PaymentJournalMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentLedgerService {

    @Autowired
    private PaymentIntentMapper paymentIntentMapper;

    @Autowired
    private PaymentJournalMapper paymentJournalMapper;

    @Autowired
    private JournalEntryMapper journalEntryMapper;

    @Autowired
    private JournalService journalService;

    @Autowired
    private LedgerAccountService ledgerAccountService;

    @Transactional
    public PaymentIntent completePayment(
            UUID paymentId) {

        // 1. Lock payment
        PaymentIntent payment =
                paymentIntentMapper.findByIdForUpdate(paymentId);

        if (payment == null) {
            throw new IllegalStateException("Payment not found");
        }

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return payment;
        }

        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Payment is not in PROCESSING status"
            );
        }

        if (payment.getProviderReference() == null) {
            throw new IllegalStateException(
                    "Provider success is not confirmed"
            );
        }

        // 2. Check existing capture journal
        PaymentJournal mapping =
                paymentJournalMapper.findByPaymentAndOperation(
                        paymentId, "CAPTURE"
                );

        if (mapping == null) {

            // 3. Resolve ledger accounts
            LedgerAccount clearing = ledgerAccountService.getOrCreateAccount(
                    OwnerType.PLATFORM,
                    "PROVIDER_CLEARING",
                    payment.getCurrency(),
                    AccountType.ASSET
            );

            LedgerAccount payable = ledgerAccountService.getOrCreateAccount(
                    OwnerType.MERCHANT,
                    payment.getMerchantId().toString(),
                    payment.getCurrency(),
                    AccountType.LIABILITY
            );

            validateAccounts(payment, clearing, payable);

            // 4. Create journal
            JournalEntry journal = new JournalEntry(
                    UUID.randomUUID(),
                    payment.getCurrency(),
                    Instant.now()
            );

            journal.addLine(new JournalLine(
                    UUID.randomUUID(),
                    clearing.getId(),
                    EntrySide.DEBIT,
                    payment.getAmount()
            ));

            journal.addLine(new JournalLine(
                    UUID.randomUUID(),
                    payable.getId(),
                    EntrySide.CREDIT,
                    payment.getAmount()
            ));

            journalService.createDraft(journal);

            // 5. Post journal
            journalService.postJournal(journal.getId());

            // 6. Save payment-journal mapping
            mapping = PaymentJournal.capture(
                    paymentId,
                    journal.getId()
            );

            if (paymentJournalMapper.insert(mapping) != 1) {
                throw new IllegalStateException(
                        "Failed to save payment journal mapping"
                );
            }

        } else {

            // Reuse existing journal
            JournalEntry journal =
                    journalEntryMapper.findByIdForUpdate(
                            mapping.journalEntryId()
                    );

            if (journal == null) {
                throw new IllegalStateException(
                        "Mapped journal not found"
                );
            }

            if (journal.getStatus() == JournalEntryStatus.DRAFT) {
                journalService.postJournal(journal.getId());
            } else if (journal.getStatus() != JournalEntryStatus.POSTED) {
                throw new IllegalStateException(
                        "Unexpected journal status"
                );
            }
        }

        // 7. Mark payment succeeded
        payment.succeed(true, true);

        if (paymentIntentMapper.updateStatus(
                payment, PaymentStatus.PROCESSING) != 1) {
            throw new IllegalStateException(
                    "Failed to complete payment"
            );
        }

        return payment;
    }

    private void validateAccounts(
            PaymentIntent payment,
            LedgerAccount clearing,
            LedgerAccount payable) {

        if (clearing == null || payable == null) {
            throw new IllegalStateException(
                    "Ledger account not found"
            );
        }

        if (clearing.getStatus() != AccountStatus.ACTIVE
                || payable.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Ledger account is not active"
            );
        }

        if (!payment.getCurrency().equals(clearing.getCurrency())
                || !payment.getCurrency().equals(payable.getCurrency())) {
            throw new IllegalStateException(
                    "Ledger account currency mismatch"
            );
        }

        if (clearing.getAccountType() != AccountType.ASSET
                || clearing.getOwnerType() != OwnerType.PLATFORM) {
            throw new IllegalStateException(
                    "Invalid provider clearing account"
            );
        }

        if (payable.getAccountType() != AccountType.LIABILITY
                || payable.getOwnerType() != OwnerType.MERCHANT
                || !payable.getOwnerId().equals(
                payment.getMerchantId().toString())) {
            throw new IllegalStateException(
                    "Invalid merchant payable account"
            );
        }
    }
}


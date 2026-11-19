package com.globalpay.lab.refund.application;

import com.globalpay.lab.ledger.application.JournalService;
import com.globalpay.lab.ledger.application.LedgerAccountService;
import com.globalpay.lab.ledger.domain.*;
import com.globalpay.lab.ledger.infrastructure.JournalEntryMapper;
import com.globalpay.lab.payment.domain.PaymentJournal;
import com.globalpay.lab.payment.infrastructure.PaymentJournalMapper;
import com.globalpay.lab.refund.domain.*;
import com.globalpay.lab.refund.infrastructure.RefundJournalMapper;
import com.globalpay.lab.refund.infrastructure.RefundMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefundLedgerService {

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private RefundJournalMapper refundJournalMapper;

    @Autowired
    private PaymentJournalMapper paymentJournalMapper;

    @Autowired
    private JournalEntryMapper journalEntryMapper;

    @Autowired
    private JournalService journalService;

    @Autowired
    private LedgerAccountService ledgerAccountService;

    @Transactional
    public Refund completeRefund(UUID refundId) {

        Refund refund = refundMapper.findByIdForUpdate(refundId);

        if (refund == null) {
            throw new IllegalStateException("Refund not found");
        }

        if (refund.getStatus() == RefundStatus.SUCCEEDED) {
            return refund;
        }

        if (refund.getStatus() != RefundStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Refund is not processing"
            );
        }

        if (refund.getProviderReference() == null) {
            throw new IllegalStateException(
                    "Provider refund success is not confirmed"
            );
        }

        // Verify the original payment was posted.
        PaymentJournal capture =
                paymentJournalMapper.findByPaymentAndOperation(
                        refund.getPaymentId(), "CAPTURE"
                );

        if (capture == null) {
            throw new IllegalStateException(
                    "Original capture journal not found"
            );
        }

        JournalEntry captureJournal =
                journalEntryMapper.findByIdForUpdate(
                        capture.journalEntryId()
                );

        if (captureJournal == null
                || captureJournal.getStatus() != JournalEntryStatus.POSTED) {
            throw new IllegalStateException(
                    "Original capture journal is not posted"
            );
        }

        RefundJournal mapping =
                refundJournalMapper.findByRefundId(refundId);

        if (mapping == null) {

            LedgerAccount clearing =
                    ledgerAccountService.getOrCreateAccount(
                            OwnerType.PLATFORM,
                            "PROVIDER_CLEARING",
                            refund.getCurrency(),
                            AccountType.ASSET
                    );

            LedgerAccount payable =
                    ledgerAccountService.getOrCreateAccount(
                            OwnerType.MERCHANT,
                            refund.getMerchantId().toString(),
                            refund.getCurrency(),
                            AccountType.LIABILITY
                    );

            validateAccounts(refund, clearing, payable);

            JournalEntry journal = new JournalEntry(
                    UUID.randomUUID(),
                    refund.getCurrency(),
                    Instant.now()
            );

            journal.addLine(new JournalLine(
                    UUID.randomUUID(),
                    payable.getId(),
                    EntrySide.DEBIT,
                    refund.getAmount()
            ));

            journal.addLine(new JournalLine(
                    UUID.randomUUID(),
                    clearing.getId(),
                    EntrySide.CREDIT,
                    refund.getAmount()
            ));

            journalService.createDraft(journal);
            journalService.postJournal(journal.getId());

            mapping = RefundJournal.create(
                    refundId,
                    journal.getId()
            );

            if (refundJournalMapper.insert(mapping) != 1) {
                throw new IllegalStateException(
                        "Failed to save refund journal mapping"
                );
            }

        } else {

            JournalEntry journal =
                    journalEntryMapper.findByIdForUpdate(
                            mapping.journalEntryId()
                    );

            if (journal == null) {
                throw new IllegalStateException(
                        "Mapped refund journal not found"
                );
            }

            if (journal.getStatus() == JournalEntryStatus.DRAFT) {
                journalService.postJournal(journal.getId());
            } else if (journal.getStatus() != JournalEntryStatus.POSTED) {
                throw new IllegalStateException(
                        "Unexpected refund journal status"
                );
            }
        }

        refund.succeed(true, true);

        if (refundMapper.updateStatus(
                refund, RefundStatus.PROCESSING
        ) != 1) {
            throw new IllegalStateException(
                    "Failed to complete refund"
            );
        }

        return refund;
    }

    private void validateAccounts(
            Refund refund,
            LedgerAccount clearing,
            LedgerAccount payable) {

        if (clearing.getStatus() != AccountStatus.ACTIVE
                || payable.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Refund ledger account is not active"
            );
        }

        if (!refund.getCurrency().equals(clearing.getCurrency())
                || !refund.getCurrency().equals(payable.getCurrency())) {
            throw new IllegalStateException(
                    "Refund ledger currency mismatch"
            );
        }

        if (clearing.getOwnerType() != OwnerType.PLATFORM
                || clearing.getAccountType() != AccountType.ASSET
                || !"PROVIDER_CLEARING".equals(clearing.getOwnerId())) {
            throw new IllegalStateException(
                    "Invalid provider clearing account"
            );
        }

        if (payable.getOwnerType() != OwnerType.MERCHANT
                || payable.getAccountType() != AccountType.LIABILITY
                || !refund.getMerchantId().toString()
                .equals(payable.getOwnerId())) {
            throw new IllegalStateException(
                    "Invalid merchant payable account"
            );
        }
    }
}


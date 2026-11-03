package com.globalpay.lab.mockpsp.application;

import com.globalpay.lab.mockpsp.domain.ProviderOperation;
import com.globalpay.lab.mockpsp.domain.ProviderOperationStatus;
import com.globalpay.lab.mockpsp.infrastructure.ProviderOperationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class MockPaymentService {

    @Autowired
    private ProviderOperationMapper mapper;

    public ProviderOperation process(MockPaymentRequest request) {
        validateRequest(request);

        Instant now = Instant.now();

        ProviderOperation operation = new ProviderOperation(
                UUID.randomUUID(),
                request.paymentId(),
                request.operationId(),
                request.amount(),
                request.currency(),
                ProviderOperationStatus.PROCESSING,
                null,
                now,
                now
        );

        int inserted = mapper.insertIfAbsent(operation);

        if (inserted == 0) {
            ProviderOperation existing =
                    mapper.findByOperationId(request.operationId());

            if (existing == null) {
                throw new IllegalStateException(
                        "Provider operation not found"
                );
            }

            validateSameRequest(existing, request);
            return existing;
        }

        // First version: simulate successful payment.
        operation.succeed("mock-" + operation.getId());

        if (mapper.updateStatus(
                operation,
                ProviderOperationStatus.PROCESSING
        ) != 1) {
            throw new IllegalStateException(
                    "Failed to complete provider operation"
            );
        }

        return operation;
    }

    public ProviderOperation getResult(String operationId) {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Operation ID must not be blank"
            );
        }

        return mapper.findByOperationId(operationId);
    }

    private void validateRequest(MockPaymentRequest request) {
        if (request == null
                || request.paymentId() == null
                || request.operationId() == null
                || request.operationId().isBlank()
                || request.amount() == null
                || request.currency() == null) {
            throw new IllegalArgumentException(
                    "Invalid provider payment request"
            );
        }
    }

    private void validateSameRequest(
            ProviderOperation existing,
            MockPaymentRequest request) {

        if (!existing.getPaymentId().equals(request.paymentId())
                || existing.getAmount().compareTo(request.amount()) != 0
                || !existing.getCurrency().equals(request.currency())) {

            throw new IllegalArgumentException(
                    "Operation ID reused with different payment details"
            );
        }
    }
}


package com.globalpay.lab.mockpsp.infrastructure;

import com.globalpay.lab.mockpsp.domain.ProviderOperation;
import com.globalpay.lab.mockpsp.domain.ProviderOperationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class ProviderOperationMapperTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private ProviderOperationMapper mapper;

    private ProviderOperation createOperation() {
        Instant now = Instant.now();

        return new ProviderOperation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "operation-" + UUID.randomUUID(),
                new BigDecimal("100.00"),
                Currency.getInstance("AUD"),
                ProviderOperationStatus.PROCESSING,
                null,
                now,
                now
        );
    }

    @Test
    void shouldInsertAndFindProviderOperation() {
        ProviderOperation operation = createOperation();

        assertEquals(1, mapper.insertIfAbsent(operation));

        ProviderOperation saved =
                mapper.findByOperationId(operation.getOperationId());

        assertNotNull(saved);
        assertEquals(operation.getId(), saved.getId());
        assertEquals(operation.getPaymentId(), saved.getPaymentId());
        assertEquals(operation.getOperationId(), saved.getOperationId());
        assertEquals(
                0,
                operation.getAmount().compareTo(saved.getAmount())
        );
        assertEquals(operation.getCurrency(), saved.getCurrency());
        assertEquals(
                ProviderOperationStatus.PROCESSING,
                saved.getStatus()
        );
        assertNull(saved.getProviderReference());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void shouldNotInsertDuplicateOperation() {
        ProviderOperation first = createOperation();

        ProviderOperation duplicate = new ProviderOperation(
                UUID.randomUUID(),
                first.getPaymentId(),
                first.getOperationId(),
                first.getAmount(),
                first.getCurrency(),
                ProviderOperationStatus.PROCESSING,
                null,
                Instant.now(),
                Instant.now()
        );

        assertEquals(1, mapper.insertIfAbsent(first));
        assertEquals(0, mapper.insertIfAbsent(duplicate));

        ProviderOperation saved =
                mapper.findByOperationId(first.getOperationId());

        assertNotNull(saved);
        assertEquals(first.getId(), saved.getId());
    }

    @Test
    void shouldUpdateProviderOperationStatus() {
        ProviderOperation operation = createOperation();

        assertEquals(1, mapper.insertIfAbsent(operation));

        operation.succeed("mock-ref-123");

        assertEquals(
                1,
                mapper.updateStatus(
                        operation,
                        ProviderOperationStatus.PROCESSING
                )
        );

        ProviderOperation saved =
                mapper.findByOperationId(operation.getOperationId());

        assertNotNull(saved);
        assertEquals(
                ProviderOperationStatus.SUCCEEDED,
                saved.getStatus()
        );
        assertEquals("mock-ref-123", saved.getProviderReference());
    }

    @Test
    void shouldNotUpdateWithStaleStatus() {
        ProviderOperation operation = createOperation();

        assertEquals(1, mapper.insertIfAbsent(operation));

        operation.succeed("mock-ref-456");

        assertEquals(
                1,
                mapper.updateStatus(
                        operation,
                        ProviderOperationStatus.PROCESSING
                )
        );

        assertEquals(
                0,
                mapper.updateStatus(
                        operation,
                        ProviderOperationStatus.PROCESSING
                )
        );
    }

    @Test
    void shouldRejectInvalidDomainStatusTransition() {
        ProviderOperation operation = createOperation();

        operation.succeed("mock-ref-789");

        assertThrows(
                IllegalStateException.class,
                () -> operation.decline("mock-ref-789")
        );
    }

    @Test
    void shouldRejectInvalidAmount() {
        Instant now = Instant.now();

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderOperation(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "operation-" + UUID.randomUUID(),
                        BigDecimal.ZERO,
                        Currency.getInstance("AUD"),
                        ProviderOperationStatus.PROCESSING,
                        null,
                        now,
                        now
                )
        );
    }
}

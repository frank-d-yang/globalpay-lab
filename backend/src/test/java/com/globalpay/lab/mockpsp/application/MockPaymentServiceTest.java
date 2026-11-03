
package com.globalpay.lab.mockpsp.application;

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
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class MockPaymentServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockPaymentService service;

    private MockPaymentRequest createRequest() {
        return new MockPaymentRequest(
                UUID.randomUUID(),
                "operation-" + UUID.randomUUID(),
                new BigDecimal("100.00"),
                Currency.getInstance("AUD")
        );
    }

    @Test
    void shouldProcessPaymentSuccessfully() {
        MockPaymentRequest request = createRequest();

        ProviderOperation result = service.process(request);

        assertEquals(ProviderOperationStatus.SUCCEEDED, result.getStatus());
        assertNotNull(result.getProviderReference());
        assertEquals(request.paymentId(), result.getPaymentId());
        assertEquals(0, request.amount().compareTo(result.getAmount()));
        assertEquals(request.currency(), result.getCurrency());
    }

    @Test
    void shouldReturnSameResultForDuplicateRequest() {
        MockPaymentRequest request = createRequest();

        ProviderOperation first = service.process(request);
        ProviderOperation second = service.process(request);

        assertEquals(first.getId(), second.getId());
        assertEquals(first.getProviderReference(), second.getProviderReference());
        assertEquals(ProviderOperationStatus.SUCCEEDED, second.getStatus());
    }

    @Test
    void shouldRejectSameOperationIdWithDifferentAmount() {
        MockPaymentRequest request = createRequest();
        service.process(request);

        MockPaymentRequest different = new MockPaymentRequest(
                request.paymentId(),
                request.operationId(),
                new BigDecimal("200.00"),
                request.currency()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.process(different)
        );
    }

    @Test
    void shouldRejectSameOperationIdWithDifferentCurrency() {
        MockPaymentRequest request = createRequest();
        service.process(request);

        MockPaymentRequest different = new MockPaymentRequest(
                request.paymentId(),
                request.operationId(),
                request.amount(),
                Currency.getInstance("USD")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.process(different)
        );
    }

    @Test
    void shouldRejectSameOperationIdWithDifferentPaymentId() {
        MockPaymentRequest request = createRequest();
        service.process(request);

        MockPaymentRequest different = new MockPaymentRequest(
                UUID.randomUUID(),
                request.operationId(),
                request.amount(),
                request.currency()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.process(different)
        );
    }

    @Test
    void shouldFindExistingProviderResult() {
        MockPaymentRequest request = createRequest();

        ProviderOperation created = service.process(request);
        ProviderOperation saved = service.getResult(request.operationId());

        assertNotNull(saved);
        assertEquals(created.getId(), saved.getId());
        assertEquals(created.getStatus(), saved.getStatus());
        assertEquals(created.getProviderReference(), saved.getProviderReference());
    }

    @Test
    void shouldReturnNullForUnknownOperation() {
        assertNull(service.getResult("operation-" + UUID.randomUUID()));
    }
}

package com.globalpay.lab.payment.infrastructure.provider;

import com.globalpay.lab.payment.application.port.PaymentProvider;
import com.globalpay.lab.payment.application.port.ProviderOutcome;
import com.globalpay.lab.payment.application.port.ProviderResult;
import com.globalpay.lab.payment.domain.PaymentIntent;

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
class LocalMockPspAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private PaymentProvider paymentProvider;

    private PaymentIntent createPayment() {
        return PaymentIntent.create(
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                Currency.getInstance("AUD"),
                UUID.randomUUID().toString()
        );
    }

    @Test
    void shouldProcessPaymentSuccessfully() {
        PaymentIntent payment = createPayment();
        String operationId = "payment-" + payment.getId();

        ProviderResult result =
                paymentProvider.process(payment, operationId);

        assertEquals(
                ProviderOutcome.SUCCEEDED,
                result.outcome()
        );

        assertNotNull(result.providerReference());
    }

    @Test
    void shouldReturnSameResultForDuplicateRequest() {
        PaymentIntent payment = createPayment();
        String operationId = "payment-" + payment.getId();

        ProviderResult first =
                paymentProvider.process(payment, operationId);

        ProviderResult second =
                paymentProvider.process(payment, operationId);

        assertEquals(first.outcome(), second.outcome());
        assertEquals(
                first.providerReference(),
                second.providerReference()
        );
    }

    @Test
    void shouldQueryExistingProviderResult() {
        PaymentIntent payment = createPayment();
        String operationId = "payment-" + payment.getId();

        ProviderResult processed =
                paymentProvider.process(payment, operationId);

        ProviderResult queried =
                paymentProvider.getResult(operationId);

        assertEquals(processed.outcome(), queried.outcome());
        assertEquals(
                processed.providerReference(),
                queried.providerReference()
        );
    }

    @Test
    void shouldReturnUnknownForMissingOperation() {
        ProviderResult result =
                paymentProvider.getResult(
                        "payment-" + UUID.randomUUID()
                );

        assertEquals(
                ProviderOutcome.UNKNOWN,
                result.outcome()
        );
    }

    @Test
    void shouldRejectOperationIdReusedByAnotherPayment() {
        PaymentIntent first = createPayment();
        PaymentIntent second = createPayment();

        String operationId = "payment-" + first.getId();

        paymentProvider.process(first, operationId);

        assertThrows(
                IllegalArgumentException.class,
                () -> paymentProvider.process(second, operationId)
        );
    }
}


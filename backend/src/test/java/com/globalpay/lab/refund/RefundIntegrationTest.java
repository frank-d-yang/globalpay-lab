package com.globalpay.lab.refund;

import com.globalpay.lab.merchant.application.MerchantService;
import com.globalpay.lab.merchant.domain.Merchant;
import com.globalpay.lab.mockpsp.domain.ProviderOperation;
import com.globalpay.lab.mockpsp.domain.ProviderOperationStatus;
import com.globalpay.lab.mockpsp.infrastructure.ProviderOperationMapper;
import com.globalpay.lab.payment.domain.PaymentIntent;
import com.globalpay.lab.payment.infrastructure.PaymentIntentMapper;
import com.globalpay.lab.refund.domain.Refund;
import com.globalpay.lab.refund.domain.RefundStatus;
import com.globalpay.lab.refund.infrastructure.RefundMapper;
import com.globalpay.lab.security.application.MerchantApiKeyService;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RefundIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private MerchantApiKeyService apiKeyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentIntentMapper paymentIntentMapper;

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private ProviderOperationMapper providerOperationMapper;

    @Test
    void shouldRefundPaymentAndReverseLedger() throws Exception {

        Merchant merchant =
                merchantService.createMerchant("Refund E2E Merchant");

        String apiKey =
                apiKeyService.issueKey(merchant.getId());

        UUID paymentId = createPayment(apiKey);

        mockMvc.perform(
                        post("/api/v1/payments/{id}/process", paymentId)
                                .header("X-API-Key", apiKey)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("SUCCEEDED"));

        assertBalance(merchant.getId(), "100.00");

        String request = """
                {
                    "paymentId": "%s"
                }
                """.formatted(paymentId);

        MvcResult result = mockMvc.perform(
                        post("/api/v1/refunds")
                                .header("X-API-Key", apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentId")
                        .value(paymentId.toString()))
                .andExpect(jsonPath("$.data.status")
                        .value("SUCCEEDED"))
                .andReturn();

        UUID refundId = UUID.fromString(
                JsonPath.read(
                        result.getResponse().getContentAsString(),
                        "$.data.id"
                )
        );

        mockMvc.perform(
                        get("/api/v1/refunds/{id}", refundId)
                                .header("X-API-Key", apiKey)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.id")
                        .value(refundId.toString()));

        assertBalance(merchant.getId(), "0");

        assertEquals(1L, countRefundJournals(refundId));
    }

    private UUID createPayment(String apiKey) throws Exception {

        String request = """
                {
                    "amount": 100.00,
                    "currency": "AUD"
                }
                """;

        MvcResult result = mockMvc.perform(
                        post("/api/v1/payments")
                                .header("X-API-Key", apiKey)
                                .header("Idempotency-Key",
                                        UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isOk())
                .andReturn();

        String id = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.id"
        );

        return UUID.fromString(id);
    }

    private void assertBalance(
            UUID merchantId, String expected) {

        BigDecimal balance = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(
                    SUM(
                        CASE
                            WHEN jl.side = 'CREDIT'
                                THEN jl.amount
                            ELSE -jl.amount
                        END
                    ), 0
                )
                FROM ledger_account la
                LEFT JOIN journal_line jl
                    ON jl.ledger_account_id = la.id
                LEFT JOIN journal_entry je
                    ON je.id = jl.journal_entry_id
                WHERE la.owner_type = 'MERCHANT'
                  AND la.owner_id = ?
                  AND la.account_type = 'LIABILITY'
                  AND la.currency = 'AUD'
                  AND (je.status = 'POSTED' OR je.id IS NULL)
                """,
                BigDecimal.class,
                merchantId.toString()
        );

        assertNotNull(balance);
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(balance)
        );
    }

    private long countRefundJournals(UUID refundId) {

        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refund_journal
                WHERE refund_id = ?
                """,
                Long.class,
                refundId
        );

        return count;
    }


    @Test
    void shouldNotRefundPaymentTwice() throws Exception {

        Merchant merchant =
                merchantService.createMerchant("Refund Idempotency Merchant");

        String apiKey =
                apiKeyService.issueKey(merchant.getId());

        UUID paymentId = createPayment(apiKey);

        // Complete the original payment.
        mockMvc.perform(
                post("/api/v1/payments/{id}/process", paymentId)
                        .header("X-API-Key", apiKey)
        ).andExpect(status().isOk());

        assertBalance(merchant.getId(), "100.00");

        String request = """
            {
                "paymentId": "%s"
            }
            """.formatted(paymentId);

        // First refund request.
        MvcResult first = mockMvc.perform(
                        post("/api/v1/refunds")
                                .header("X-API-Key", apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("SUCCEEDED"))
                .andReturn();

        // Retry the same refund request.
        MvcResult second = mockMvc.perform(
                        post("/api/v1/refunds")
                                .header("X-API-Key", apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("SUCCEEDED"))
                .andReturn();

        UUID firstRefundId = UUID.fromString(
                JsonPath.read(
                        first.getResponse().getContentAsString(),
                        "$.data.id"
                )
        );

        UUID secondRefundId = UUID.fromString(
                JsonPath.read(
                        second.getResponse().getContentAsString(),
                        "$.data.id"
                )
        );

        assertEquals(firstRefundId, secondRefundId);

        Long refundCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refund
                WHERE payment_id = ?
                """,
                Long.class,
                paymentId
        );

        assertEquals(1L, refundCount);

        Long providerOperationCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM provider_operation
                WHERE operation_id = ?
                """,
                Long.class,
                "refund-" + firstRefundId
        );

        assertEquals(1L, providerOperationCount);

        assertEquals(1L, countRefundJournals(firstRefundId));

        // The merchant must not be debited twice.
        assertBalance(merchant.getId(), "0");
    }


    @Test
    void shouldPreventCrossMerchantRefund() throws Exception {

        Merchant merchantA =
                merchantService.createMerchant("Merchant A");

        Merchant merchantB =
                merchantService.createMerchant("Merchant B");

        String apiKeyA =
                apiKeyService.issueKey(merchantA.getId());

        String apiKeyB =
                apiKeyService.issueKey(merchantB.getId());

        UUID paymentId = createPayment(apiKeyB);

        mockMvc.perform(
                        post("/api/v1/payments/{id}/process", paymentId)
                                .header("X-API-Key", apiKeyB)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("SUCCEEDED"));

        String request = """
            {
                "paymentId": "%s"
            }
            """.formatted(paymentId);

        // Merchant A attempts to refund Merchant B's payment.
        mockMvc.perform(
                        post("/api/v1/refunds")
                                .header("X-API-Key", apiKeyA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(jsonPath("$.code").value(404));

        Long refundCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refund
                WHERE payment_id = ?
                """,
                Long.class,
                paymentId
        );

        assertEquals(0L, refundCount);

        assertBalance(merchantB.getId(), "100.00");
    }


    @Test
    void shouldPreventCrossMerchantRefundQuery() throws Exception {

        Merchant merchantA =
                merchantService.createMerchant("Merchant A");

        Merchant merchantB =
                merchantService.createMerchant("Merchant B");

        String apiKeyA =
                apiKeyService.issueKey(merchantA.getId());

        String apiKeyB =
                apiKeyService.issueKey(merchantB.getId());

        UUID paymentId = createPayment(apiKeyB);

        mockMvc.perform(
                        post("/api/v1/payments/{id}/process", paymentId)
                                .header("X-API-Key", apiKeyB)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("SUCCEEDED"));

        String request = """
            {
                "paymentId": "%s"
            }
            """.formatted(paymentId);

        MvcResult result = mockMvc.perform(
                        post("/api/v1/refunds")
                                .header("X-API-Key", apiKeyB)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("SUCCEEDED"))
                .andReturn();

        UUID refundId = UUID.fromString(
                JsonPath.read(
                        result.getResponse().getContentAsString(),
                        "$.data.id"
                )
        );

        // Merchant A cannot access Merchant B's refund.
        mockMvc.perform(
                        get("/api/v1/refunds/{id}", refundId)
                                .header("X-API-Key", apiKeyA)
                )
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist());

        // Merchant B can access its own refund.
        mockMvc.perform(
                        get("/api/v1/refunds/{id}", refundId)
                                .header("X-API-Key", apiKeyB)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id")
                        .value(refundId.toString()))
                .andExpect(jsonPath("$.data.status")
                        .value("SUCCEEDED"));
    }


    @Test
    void shouldRecoverRefundAfterLostProviderResponse()
            throws Exception {

        Merchant merchant =
                merchantService.createMerchant("Refund Recovery Merchant");

        String apiKey = apiKeyService.issueKey(merchant.getId());

        UUID paymentId = createPayment(apiKey);

        mockMvc.perform(
                        post("/api/v1/payments/{id}/process", paymentId)
                                .header("X-API-Key", apiKey)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("SUCCEEDED"));

        assertBalance(merchant.getId(), "100.00");

        PaymentIntent payment = paymentIntentMapper.findById(paymentId);

        Refund refund = Refund.create(payment);
        assertEquals(1, refundMapper.insertIfAbsent(refund));

        refund.startProcessing();

        assertEquals(1, refundMapper.updateStatus(
                refund, RefundStatus.CREATED
        ));

        // Simulate PSP success before GlobalPay saves the result.
        String operationId = "refund-" + refund.getId();
        String providerReference = "mock-recovered-" + refund.getId();

        Instant now = Instant.now();

        ProviderOperation operation = new ProviderOperation(
                UUID.randomUUID(),
                paymentId,
                operationId,
                refund.getAmount(),
                refund.getCurrency(),
                ProviderOperationStatus.SUCCEEDED,
                providerReference,
                now,
                now
        );

        assertEquals(
                1,
                providerOperationMapper.insertIfAbsent(operation)
        );

        // GlobalPay has not recorded the provider result yet.
        Refund beforeRetry = refundMapper.findByPaymentId(paymentId);

        assertEquals(RefundStatus.PROCESSING, beforeRetry.getStatus());
        assertNull(beforeRetry.getProviderReference());

        String request = """
            {
                "paymentId": "%s"
            }
            """.formatted(paymentId);

        // Retry through the real HTTP API.
        mockMvc.perform(
                        post("/api/v1/refunds")
                                .header("X-API-Key", apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id")
                        .value(refund.getId().toString()))
                .andExpect(jsonPath("$.data.status")
                        .value("SUCCEEDED"));

        Refund recovered = refundMapper.findByPaymentId(paymentId);

        assertEquals(RefundStatus.SUCCEEDED, recovered.getStatus());
        assertEquals(providerReference, recovered.getProviderReference());

        Long providerOperationCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM provider_operation
                WHERE operation_id = ?
                """,
                Long.class,
                operationId
        );

        assertEquals(1L, providerOperationCount);
        assertEquals(1L, countRefundJournals(refund.getId()));

        assertBalance(merchant.getId(), "0");
    }


}

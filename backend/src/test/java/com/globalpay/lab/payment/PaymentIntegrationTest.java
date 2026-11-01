package com.globalpay.lab.payment;

import com.globalpay.lab.merchant.application.MerchantService;
import com.globalpay.lab.merchant.domain.Merchant;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentIntegrationTest {

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

    @Test
    void shouldCreateAndQueryPayment() throws Exception {

        Merchant merchant = merchantService.createMerchant("UOW");
        String apiKey = apiKeyService.issueKey(merchant.getId());
        String key = UUID.randomUUID().toString();

        MvcResult created = createPayment(apiKey, key, "100.00");

        assertEquals(200, responseCode(created));

        UUID paymentId = paymentId(created);

        mockMvc.perform(
                        get("/api/v1/payments/{id}", paymentId)
                                .header("X-API-Key", apiKey)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id")
                        .value(paymentId.toString()))
                .andExpect(jsonPath("$.data.currency").value("AUD"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    void shouldReturnSamePaymentForRepeatedRequest() throws Exception {

        Merchant merchant = merchantService.createMerchant("UOW");
        String apiKey = apiKeyService.issueKey(merchant.getId());
        String key = UUID.randomUUID().toString();

        MvcResult first = createPayment(apiKey, key, "100.00");
        MvcResult second = createPayment(apiKey, key, "100.0");

        assertEquals(200, responseCode(first));
        assertEquals(200, responseCode(second));
        assertEquals(paymentId(first), paymentId(second));
        assertEquals(1L, countPayments(merchant.getId(), key));
    }

    @Test
    void shouldRejectDifferentAmountWithSameKey() throws Exception {

        Merchant merchant = merchantService.createMerchant("UOW");
        String apiKey = apiKeyService.issueKey(merchant.getId());
        String key = UUID.randomUUID().toString();

        createPayment(apiKey, key, "100.00");

        MvcResult conflict = createPayment(apiKey, key, "200.00");

        assertEquals(409, responseCode(conflict));
        assertEquals(1L, countPayments(merchant.getId(), key));
    }

    @Test
    void shouldPreventCrossMerchantAccess() throws Exception {

        Merchant merchantA = merchantService.createMerchant("Merchant A");
        Merchant merchantB = merchantService.createMerchant("Merchant B");

        String apiKeyA = apiKeyService.issueKey(merchantA.getId());
        String apiKeyB = apiKeyService.issueKey(merchantB.getId());

        UUID id = paymentId(createPayment(
                apiKeyB,
                UUID.randomUUID().toString(),
                "100.00"
        ));

        MvcResult result = mockMvc.perform(
                get("/api/v1/payments/{id}", id)
                        .header("X-API-Key", apiKeyA)
        ).andReturn();

        assertEquals(404, responseCode(result));
        assertFalse(result.getResponse()
                .getContentAsString().contains(id.toString()));
    }

    @Test
    void shouldCreateOnlyOnePaymentForConcurrentRequests()
            throws Exception {

        Merchant merchant = merchantService.createMerchant("UOW");
        String apiKey = apiKeyService.issueKey(merchant.getId());
        String key = UUID.randomUUID().toString();

        List<MvcResult> results =
                runConcurrentRequests(apiKey, key, "100.00", "100.00");

        assertEquals(200, responseCode(results.get(0)));
        assertEquals(200, responseCode(results.get(1)));

        assertEquals(
                paymentId(results.get(0)),
                paymentId(results.get(1))
        );

        assertEquals(1L, countPayments(merchant.getId(), key));
    }

    @Test
    void shouldRejectConcurrentRequestsWithDifferentAmounts()
            throws Exception {

        Merchant merchant = merchantService.createMerchant("UOW");
        String apiKey = apiKeyService.issueKey(merchant.getId());
        String key = UUID.randomUUID().toString();

        List<MvcResult> results =
                runConcurrentRequests(apiKey, key, "100.00", "200.00");

        long successCount = results.stream()
                .filter(r -> responseCode(r) == 200)
                .count();

        long conflictCount = results.stream()
                .filter(r -> responseCode(r) == 409)
                .count();

        assertEquals(1, successCount);
        assertEquals(1, conflictCount);
        assertEquals(1L, countPayments(merchant.getId(), key));
    }

    private MvcResult createPayment(
            String apiKey, String key, String amount)
            throws Exception {

        String json = """
                {
                    "amount": %s,
                    "currency": "AUD"
                }
                """.formatted(amount);

        return mockMvc.perform(
                post("/api/v1/payments")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        ).andReturn();
    }

    private UUID paymentId(MvcResult result) {

        String body = new String(
                result.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8
        );

        String id = JsonPath.read(body, "$.data.id");

        return UUID.fromString(id);
    }

    private int responseCode(MvcResult result) {

        String body = new String(
                result.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8
        );

        return JsonPath.read(body, "$.code");
    }

    private long countPayments(UUID merchantId, String key) {

        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM payment_intent
                WHERE merchant_id = ?
                  AND idempotency_key = ?
                """,
                Long.class,
                merchantId,
                key
        );

        return count;
    }

    private List<MvcResult> runConcurrentRequests(
            String apiKey,
            String key,
            String amountA,
            String amountB) throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<MvcResult> taskA = () -> {
            ready.countDown();
            if (!start.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start timed out");
            }
            return createPayment(apiKey, key, amountA);
        };

        Callable<MvcResult> taskB = () -> {
            ready.countDown();
            if (!start.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start timed out");
            }
            return createPayment(apiKey, key, amountB);
        };

        try {
            Future<MvcResult> first = executor.submit(taskA);
            Future<MvcResult> second = executor.submit(taskB);

            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();

            return List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}


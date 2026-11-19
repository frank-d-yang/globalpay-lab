package com.globalpay.lab.wallet;

import com.globalpay.lab.ledger.domain.AccountType;
import com.globalpay.lab.ledger.domain.LedgerAccount;
import com.globalpay.lab.ledger.domain.OwnerType;
import com.globalpay.lab.ledger.infrastructure.LedgerAccountMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WalletIntegrationTest {

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
    private LedgerAccountMapper ledgerAccountMapper;

    @Test
    void shouldShowBalanceAfterSuccessfulPayment() throws Exception {
        Merchant merchant = merchantService.createMerchant("Wallet Payment Test");
        String apiKey = apiKeyService.issueKey(merchant.getId());

        // No successful payment yet: no merchant payable account exists.
        mockMvc.perform(get("/api/v1/wallets")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        UUID paymentId = createPayment(apiKey, "100.00", "AUD");
        processPayment(apiKey, paymentId);

        LedgerAccount account = payableAccount(merchant.getId(), "AUD");
        assertNotNull(account);

        mockMvc.perform(get("/api/v1/wallets")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].ledgerAccountId").value(account.getId().toString()))
                .andExpect(jsonPath("$.data[0].currency").value("AUD"))
                .andExpect(jsonPath("$.data[0].recordedBalance").value(100.00));

        mockMvc.perform(get("/api/v1/wallets/{ledgerAccountId}", account.getId())
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerAccountId").value(account.getId().toString()))
                .andExpect(jsonPath("$.data.recordedBalance").value(100.00));
    }

    @Test
    void shouldAccumulatePaymentsWithoutCreatingDuplicateAccounts() throws Exception {
        Merchant merchant = merchantService.createMerchant("Wallet Balance Test");
        String apiKey = apiKeyService.issueKey(merchant.getId());

        processPayment(apiKey, createPayment(apiKey, "100.00", "AUD"));
        processPayment(apiKey, createPayment(apiKey, "200.00", "AUD"));

        mockMvc.perform(get("/api/v1/wallets")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].currency").value("AUD"))
                .andExpect(jsonPath("$.data[0].recordedBalance").value(300.00));

        assertNotNull(payableAccount(merchant.getId(), "AUD"));
        assertEquals(1, ledgerAccountMapper
                .findByOwnerId(merchant.getId().toString()).size());
    }

    @Test
    void shouldIsolateWalletsBetweenMerchants() throws Exception {
        Merchant merchantA = merchantService.createMerchant("Wallet Merchant A");
        Merchant merchantB = merchantService.createMerchant("Wallet Merchant B");
        String keyA = apiKeyService.issueKey(merchantA.getId());
        String keyB = apiKeyService.issueKey(merchantB.getId());

        processPayment(keyA, createPayment(keyA, "100.00", "AUD"));
        processPayment(keyB, createPayment(keyB, "200.00", "AUD"));

        LedgerAccount accountB = payableAccount(merchantB.getId(), "AUD");
        assertNotNull(accountB);

        mockMvc.perform(get("/api/v1/wallets")
                        .header("X-API-Key", keyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].recordedBalance").value(100.00))
                .andExpect(jsonPath("$.data[0].ledgerAccountId")
                        .value(payableAccount(merchantA.getId(), "AUD")
                                .getId().toString()));

        // Current GlobalExceptionHandler wraps ResponseStatusException as an
        // error body with HTTP 200. HTTP status normalization is a separate issue.
        MvcResult denied = mockMvc.perform(
                        get("/api/v1/wallets/{ledgerAccountId}", accountB.getId())
                                .header("X-API-Key", keyA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        assertFalse(denied.getResponse().getContentAsString()
                .contains(accountB.getId().toString()));
    }

    private UUID createPayment(String apiKey, String amount, String currency)
            throws Exception {
        String requestBody = """
                {"amount": %s, "currency": "%s"}
                """.formatted(amount, currency);

        MvcResult result = mockMvc.perform(post("/api/v1/payments")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.id");
        return UUID.fromString(id);
    }

    private void processPayment(String apiKey, UUID paymentId) throws Exception {
        mockMvc.perform(post("/api/v1/payments/{paymentId}/process", paymentId)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    private LedgerAccount payableAccount(UUID merchantId, String currency) {
        return ledgerAccountMapper.findByOwnerAndCurrencyAndType(
                OwnerType.MERCHANT,
                merchantId.toString(),
                Currency.getInstance(currency),
                AccountType.LIABILITY);
    }
}

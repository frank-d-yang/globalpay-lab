package com.globalpay.lab.security;

import com.globalpay.lab.merchant.application.MerchantService;
import com.globalpay.lab.merchant.domain.Merchant;
import com.globalpay.lab.security.application.MerchantApiKeyService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(MerchantApiKeyHttpIntegrationTest.TestRoutes.class)
class MerchantApiKeyHttpIntegrationTest {

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

    @TestConfiguration(proxyBeanMethods = false)
    static class TestRoutes {

        @Bean
        TestMerchantController testMerchantController() {
            return new TestMerchantController();
        }
    }

    @RestController
    static class TestMerchantController {

        @GetMapping("/test/merchant-identity")
        public Map<String, String> identity(
                Authentication authentication) {

            return Map.of(
                    "merchantId",
                    authentication.getPrincipal().toString()
            );
        }
    }

    @Test
    void shouldRejectMissingApiKey() throws Exception {

        mockMvc.perform(
                get("/test/merchant-identity")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidApiKey() throws Exception {

        mockMvc.perform(
                get("/test/merchant-identity")
                        .header("X-API-Key", "invalid-key")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAuthenticateMerchant() throws Exception {

        Merchant merchant =
                merchantService.createMerchant("Frank Commerce");

        String rawKey =
                apiKeyService.issueKey(merchant.getId());

        mockMvc.perform(
                        get("/test/merchant-identity")
                                .header("X-API-Key", rawKey)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId")
                        .value(merchant.getId().toString()));
    }

    @Test
    void shouldDenyLedgerAccessEvenWithValidKey()
            throws Exception {

        Merchant merchant =
                merchantService.createMerchant("Frank Commerce");

        String rawKey =
                apiKeyService.issueKey(merchant.getId());

        mockMvc.perform(
                get("/api/v1/ledger/accounts")
                        .header("X-API-Key", rawKey)
        ).andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectRevokedApiKeyOverHttp() throws Exception {

        Merchant merchant =
                merchantService.createMerchant("Frank Commerce");

        String rawKey =
                apiKeyService.issueKey(merchant.getId());

        // Extract the Key ID from the issued API Key.
        int separator = rawKey.indexOf('.');

        UUID keyId = UUID.fromString(
                rawKey.substring("gpl_test_".length(), separator)
        );

        // Revoke the API Key.
        assertTrue(apiKeyService.revokeKey(keyId));

        // The revoked API Key must no longer authenticate.
        mockMvc.perform(
                get("/test/merchant-identity")
                        .header("X-API-Key", rawKey)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectSuspendedMerchantOverHttp() throws Exception {

        Merchant merchant =
                merchantService.createMerchant("Frank Commerce");

        String rawKey =
                apiKeyService.issueKey(merchant.getId());

        // Suspend the merchant in the test database.
        int updated = jdbcTemplate.update(
                "UPDATE merchant SET status = 'SUSPENDED' WHERE id = ?",
                merchant.getId()
        );

        assertEquals(1, updated);

        // A valid API Key must not authenticate
        // when its merchant is suspended.
        mockMvc.perform(
                get("/test/merchant-identity")
                        .header("X-API-Key", rawKey)
        ).andExpect(status().isUnauthorized());
    }
}


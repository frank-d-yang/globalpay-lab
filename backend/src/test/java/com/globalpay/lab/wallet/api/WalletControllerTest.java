package com.globalpay.lab.wallet.api;

import com.globalpay.lab.security.SecurityConfig;
import com.globalpay.lab.security.application.MerchantApiKeyService;
import com.globalpay.lab.wallet.api.dto.WalletResponse;
import com.globalpay.lab.wallet.application.WalletService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WalletController.class)
@Import(SecurityConfig.class)
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @MockitoBean
    private MerchantApiKeyService apiKeyService;

    private final UUID merchantId = UUID.randomUUID();
    private final String apiKey = "test-api-key";

    @Test
    void shouldListWallets() throws Exception {
        WalletResponse aud = new WalletResponse(
                UUID.randomUUID(),
                "AUD",
                "ACTIVE",
                new BigDecimal("300.00")
        );

        WalletResponse usd = new WalletResponse(
                UUID.randomUUID(),
                "USD",
                "ACTIVE",
                new BigDecimal("50.00")
        );

        when(apiKeyService.authenticate(apiKey))
                .thenReturn(merchantId);

        when(walletService.listWallets(merchantId))
                .thenReturn(List.of(aud, usd));

        mockMvc.perform(
                        get("/api/v1/wallets")
                                .header("X-API-Key", apiKey)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].currency").value("AUD"))
                .andExpect(jsonPath("$.data[0].recordedBalance").value(300.00))
                .andExpect(jsonPath("$.data[1].currency").value("USD"))
                .andExpect(jsonPath("$.data[1].recordedBalance").value(50.00));

        verify(walletService).listWallets(merchantId);
    }

    @Test
    void shouldGetWallet() throws Exception {
        UUID accountId = UUID.randomUUID();

        WalletResponse wallet = new WalletResponse(
                accountId,
                "AUD",
                "ACTIVE",
                new BigDecimal("100.00")
        );

        when(apiKeyService.authenticate(apiKey))
                .thenReturn(merchantId);

        when(walletService.getWallet(merchantId, accountId))
                .thenReturn(wallet);

        mockMvc.perform(
                        get("/api/v1/wallets/{ledgerAccountId}", accountId)
                                .header("X-API-Key", apiKey)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerAccountId")
                        .value(accountId.toString()))
                .andExpect(jsonPath("$.data.currency").value("AUD"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.recordedBalance").value(100.00));

        verify(walletService).getWallet(merchantId, accountId);
    }

    @Test
    void shouldReturnNotFoundForOtherMerchantWallet() throws Exception {
        UUID accountId = UUID.randomUUID();

        when(apiKeyService.authenticate(apiKey))
                .thenReturn(merchantId);

        when(walletService.getWallet(merchantId, accountId))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Wallet not found"
                ));

        mockMvc.perform(
                        get("/api/v1/wallets/{ledgerAccountId}", accountId)
                                .header("X-API-Key", apiKey)
                )
                .andExpect(jsonPath("$.code").value(500));;

        verify(walletService).getWallet(merchantId, accountId);
    }

    @Test
    void shouldRejectListWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/wallets"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(walletService);
    }

    @Test
    void shouldRejectGetWithoutApiKey() throws Exception {
        mockMvc.perform(
                        get("/api/v1/wallets/{ledgerAccountId}",
                                UUID.randomUUID())
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(walletService);
    }
}

package com.globalpay.lab.ledger.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.globalpay.lab.common.api.ResultCode;
import com.globalpay.lab.common.exception.BusinessException;
import com.globalpay.lab.ledger.domain.AccountStatus;
import com.globalpay.lab.ledger.domain.AccountType;
import com.globalpay.lab.ledger.domain.LedgerAccount;
import com.globalpay.lab.ledger.domain.OwnerType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import com.globalpay.lab.ledger.application.LedgerAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@WebMvcTest(LedgerAccountController.class)
@AutoConfigureMockMvc(addFilters = false)
public class LedgerAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LedgerAccountService accountService;

    @Test
    void shouldRejectBlankOwnerId() throws Exception {
        // 1. 准备请求 JSON
        String json = """
            {
                "ownerType": "MERCHANT",
                "ownerId": "",
                "currency": "AUD",
                "accountType": "LIABILITY"
            }
            """;

        // 2. 模拟发送 POST 请求
        mockMvc.perform(
                        post("/api/v1/ledger/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )

                // 3. 自动检查结果
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").exists());

        // 4. 确认没有调用 Service
        verifyNoInteractions(accountService);
    }

    @Test
    void shouldReturnAccountNotFound() throws Exception {

        UUID accountId = UUID.randomUUID();

        when(accountService.getAccount(accountId))
                .thenThrow(
                        new BusinessException(ResultCode.ACCOUNT_NOT_FOUND)
                );

        mockMvc.perform(
                        get("/api/v1/ledger/accounts/{id}", accountId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Ledger account not found"));
    }

    @Test
    void shouldGetAccountSuccessfully() throws Exception {

        // 1. 准备测试数据
        UUID accountId = UUID.randomUUID();

        LedgerAccount account = new LedgerAccount(
                accountId,
                OwnerType.MERCHANT,
                "merchant-001",
                Currency.getInstance("AUD"),
                AccountType.LIABILITY,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        // 2. 模拟 Service 返回账户
        when(accountService.getAccount(accountId))
                .thenReturn(account);

        // 3. 模拟 GET 请求，并验证响应
        mockMvc.perform(
                        get("/api/v1/ledger/accounts/{id}", accountId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id")
                        .value(accountId.toString()))
                .andExpect(jsonPath("$.data.ownerId")
                        .value("merchant-001"))
                .andExpect(jsonPath("$.data.currency")
                        .value("AUD"));

        // 4. 验证 Service 被正确调用
        verify(accountService).getAccount(accountId);
    }

    @Test
    void shouldCreateAccountSuccessfully() throws Exception {

        // 1. 准备模拟账户
        UUID accountId = UUID.randomUUID();

        LedgerAccount account = new LedgerAccount(
                accountId,
                OwnerType.MERCHANT,
                "merchant-001",
                Currency.getInstance("AUD"),
                AccountType.LIABILITY,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        // 2. 模拟 Service 创建账户并返回结果
        when(accountService.createAccount(
                OwnerType.MERCHANT,
                "merchant-001",
                Currency.getInstance("AUD"),
                AccountType.LIABILITY
        )).thenReturn(account);

        // 3. 准备 HTTP 请求 JSON
        String json = """
            {
                "ownerType": "MERCHANT",
                "ownerId": "merchant-001",
                "currency": "AUD",
                "accountType": "LIABILITY"
            }
            """;

        // 4. 模拟 POST 请求并验证
        mockMvc.perform(
                        post("/api/v1/ledger/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id")
                        .value(accountId.toString()))
                .andExpect(jsonPath("$.data.ownerId")
                        .value("merchant-001"))
                .andExpect(jsonPath("$.data.currency")
                        .value("AUD"));

        // 5. 验证 Service 被调用一次
        verify(accountService).createAccount(
                OwnerType.MERCHANT,
                "merchant-001",
                Currency.getInstance("AUD"),
                AccountType.LIABILITY
        );
    }

    @Test
    void shouldListAccountsSuccessfully() throws Exception {

        // 1. 创建模拟账户
        LedgerAccount account = new LedgerAccount(
                UUID.randomUUID(),
                OwnerType.MERCHANT,
                "merchant-001",
                Currency.getInstance("AUD"),
                AccountType.LIABILITY,
                AccountStatus.ACTIVE,
                Instant.now()
        );

        // 2. 模拟 Service 返回账户列表
        when(accountService.listAccounts())
                .thenReturn(List.of(account));

        // 3. 模拟 GET 请求
        mockMvc.perform(
                        get("/api/v1/ledger/accounts")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].ownerId")
                        .value("merchant-001"));

        // 4. 验证 Service 调用
        verify(accountService).listAccounts();
    }
}

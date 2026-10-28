
package com.globalpay.lab.ledger;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LedgerAccountIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateAndRetrieveAccount() throws Exception {

        // 1. 准备创建账户的请求
        String ownerId = "merchant-" + UUID.randomUUID();

        String json = """
                {
                    "ownerType": "MERCHANT",
                    "ownerId": "%s",
                    "currency": "AUD",
                    "accountType": "LIABILITY"
                }
                """.formatted(ownerId);

        // 2. 通过真实业务链路创建账户
        String response = mockMvc.perform(
                        post("/api/v1/ledger/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 3. 从响应 JSON 中提取账户 ID
        String accountId = JsonPath.read(response, "$.data.id");

        // 4. 根据 ID 再次查询账户
        mockMvc.perform(
                        get("/api/v1/ledger/accounts/{id}", accountId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(accountId))
                .andExpect(jsonPath("$.data.ownerId").value(ownerId))
                .andExpect(jsonPath("$.data.currency").value("AUD"))
                .andExpect(jsonPath("$.data.accountType").value("LIABILITY"));
    }
}


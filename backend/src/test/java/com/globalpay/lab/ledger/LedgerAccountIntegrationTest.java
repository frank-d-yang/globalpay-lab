
package com.globalpay.lab.ledger;

import com.globalpay.lab.ledger.application.JournalService;
import com.globalpay.lab.ledger.application.LedgerAccountService;
import com.globalpay.lab.ledger.domain.*;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
class LedgerAccountIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LedgerAccountService ledgerAccountService;

    @Autowired
    private JournalService journalService;

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


    @Test
    void shouldReturnBalanceFromPostedJournals() throws Exception {

        Currency aud = Currency.getInstance("AUD");

        // 1. Prepare accounts
        LedgerAccount asset = ledgerAccountService.createAccount(
                OwnerType.PLATFORM,
                "platform",
                aud,
                AccountType.ASSET
        );

        LedgerAccount liability = ledgerAccountService.createAccount(
                OwnerType.MERCHANT,
                "merchant-test",
                aud,
                AccountType.LIABILITY
        );

        // 2. Empty account should return zero
        mockMvc.perform(get(
                        "/api/v1/ledger/accounts/{id}/balance",
                        asset.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(0));

        // 3. Prepare a balanced journal
        JournalEntry journal = new JournalEntry(
                UUID.randomUUID(),
                aud,
                Instant.now()
        );

        journal.addLine(new JournalLine(
                UUID.randomUUID(),
                asset.getId(),
                EntrySide.DEBIT,
                new BigDecimal("100.25")
        ));

        journal.addLine(new JournalLine(
                UUID.randomUUID(),
                liability.getId(),
                EntrySide.CREDIT,
                new BigDecimal("100.25")
        ));

        journalService.createDraft(journal);

        // 4. DRAFT must not affect account balance
        mockMvc.perform(get(
                        "/api/v1/ledger/accounts/{id}/balance",
                        asset.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(0));

        // 5. Post journal
        journalService.postJournal(journal.getId());

        // 6. Verify actual REST API response
        mockMvc.perform(get(
                        "/api/v1/ledger/accounts/{id}/balance",
                        asset.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accountId")
                        .value(asset.getId().toString()))
                .andExpect(jsonPath("$.data.currency").value("AUD"))
                .andExpect(jsonPath("$.data.balance").value(100.25));

        // 7. Verify liability balance
        mockMvc.perform(get(
                        "/api/v1/ledger/accounts/{id}/balance",
                        liability.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(100.25));
    }


    @Test
    void shouldReturnPostedJournalHistory() throws Exception {

        Currency aud = Currency.getInstance("AUD");

        // Arrange: Create accounts
        LedgerAccount asset = ledgerAccountService.createAccount(
                OwnerType.PLATFORM, "platform",
                aud, AccountType.ASSET
        );

        LedgerAccount liability = ledgerAccountService.createAccount(
                OwnerType.MERCHANT, "merchant-test",
                aud, AccountType.LIABILITY
        );

        // Arrange: Create journal
        JournalEntry journal = new JournalEntry(
                UUID.randomUUID(),
                aud,
                Instant.now().minusSeconds(60)
        );

        JournalLine debit = new JournalLine(
                UUID.randomUUID(),
                asset.getId(),
                EntrySide.DEBIT,
                new BigDecimal("100.25")
        );

        JournalLine credit = new JournalLine(
                UUID.randomUUID(),
                liability.getId(),
                EntrySide.CREDIT,
                new BigDecimal("100.25")
        );

        journal.addLine(debit);
        journal.addLine(credit);

        journalService.createDraft(journal);

        // DRAFT should not appear in history
        mockMvc.perform(get(
                        "/api/v1/ledger/accounts/{id}/journals",
                        asset.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries").isEmpty());

        // Post journal
        journalService.postJournal(journal.getId());

        // Act & Assert: Query through the REST API
        mockMvc.perform(get(
                        "/api/v1/ledger/accounts/{id}/journals",
                        asset.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accountId")
                        .value(asset.getId().toString()))
                .andExpect(jsonPath("$.data.currency").value("AUD"))
                .andExpect(jsonPath("$.data.accountType").value("ASSET"))
                .andExpect(jsonPath("$.data.entries.length()").value(1))
                .andExpect(jsonPath("$.data.entries[0].journalId")
                        .value(journal.getId().toString()))
                .andExpect(jsonPath("$.data.entries[0].lineId")
                        .value(debit.getId().toString()))
                .andExpect(jsonPath("$.data.entries[0].side").value("DEBIT"))
                .andExpect(jsonPath("$.data.entries[0].amount").value(100.25))
                .andExpect(jsonPath("$.data.entries[0].postedAt").exists());
    }

    @Test
    void shouldReturnNotFoundForUnknownAccountHistory() throws Exception {

        mockMvc.perform(get(
                        "/api/v1/ledger/accounts/{id}/journals",
                        UUID.randomUUID()))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Ledger account not found"));
    }

}


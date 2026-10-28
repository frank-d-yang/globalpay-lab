package com.globalpay.lab.ledger.api;

import com.globalpay.lab.common.api.Response;
import com.globalpay.lab.ledger.api.dto.CreateLedgerAccountRequest;
import com.globalpay.lab.ledger.api.dto.LedgerAccountResponse;
import com.globalpay.lab.ledger.application.LedgerAccountService;
import com.globalpay.lab.ledger.domain.LedgerAccount;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger/accounts")
public class LedgerAccountController {

    @Autowired
    private LedgerAccountService ledgerAccountService;

    @PostMapping
    public Response<LedgerAccountResponse> createAccount(@Valid @RequestBody CreateLedgerAccountRequest request) {
        LedgerAccount account = ledgerAccountService.createAccount(
                request.ownerType(),
                request.ownerId(),
                Currency.getInstance(request.currency()),
                request.accountType()
        );

        return Response.success(LedgerAccountResponse.from(account));
    }

    @GetMapping("/{id}")
    public Response<LedgerAccountResponse> getAccount(@PathVariable UUID id) {
        LedgerAccount account = ledgerAccountService.getAccount(id);
        return Response.success(LedgerAccountResponse.from(account));
    }

    @GetMapping
    public Response<List<LedgerAccountResponse>> listAccounts() {
        List<LedgerAccount> ledgerAccounts = ledgerAccountService.listAccounts();

        List<LedgerAccountResponse> list = ledgerAccounts.stream()
                .map(LedgerAccountResponse::from)
                .toList();
        return Response.success(list);
    }
}

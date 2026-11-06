package com.globalpay.lab.wallet.api;

import com.globalpay.lab.common.api.Response;
import com.globalpay.lab.wallet.api.dto.WalletResponse;
import com.globalpay.lab.wallet.application.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    @Autowired
    private WalletService walletService;

    @GetMapping
    public Response<List<WalletResponse>> listWallets(
            @AuthenticationPrincipal UUID merchantId) {
        return Response.success(walletService.listWallets(merchantId));
    }

    @GetMapping("/{ledgerAccountId}")
    public Response<WalletResponse> getWallet(
            @AuthenticationPrincipal UUID merchantId,
            @PathVariable UUID ledgerAccountId) {
        return Response.success(walletService.getWallet(merchantId, ledgerAccountId));
    }
}

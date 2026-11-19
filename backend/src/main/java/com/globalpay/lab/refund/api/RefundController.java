package com.globalpay.lab.refund.api;

import com.globalpay.lab.common.api.Response;
import com.globalpay.lab.refund.api.dto.RefundRequest;
import com.globalpay.lab.refund.api.dto.RefundResponse;
import com.globalpay.lab.refund.application.RefundService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {

    @Autowired
    private RefundService refundService;

    @PostMapping
    public Response<RefundResponse> refund(@AuthenticationPrincipal UUID merchantId,
                                           @Valid @RequestBody RefundRequest refundRequest) {
        return Response.success(refundService.refund(merchantId, refundRequest.paymentId()));
    }


    @GetMapping("/{refundId}")
    public Response<RefundResponse> getRefund(@AuthenticationPrincipal UUID merchantId,
                                                    @PathVariable UUID refundId) {
        return Response.success(refundService.getRefund(merchantId, refundId));
    }

}

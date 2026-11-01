package com.globalpay.lab.payment.api;

import com.globalpay.lab.common.api.Response;
import com.globalpay.lab.payment.api.dto.CreatePaymentRequest;
import com.globalpay.lab.payment.api.dto.PaymentResponse;
import com.globalpay.lab.payment.application.PaymentService;
import com.globalpay.lab.payment.domain.PaymentIntent;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Currency;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping
    public Response<PaymentResponse> createPayment(
            @AuthenticationPrincipal UUID merchantId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        PaymentIntent payment = paymentService.createPayment(
                merchantId,
                request.amount(),
                Currency.getInstance(request.currency()),
                idempotencyKey
        );

        return Response.success(PaymentResponse.from(payment));
    }

    @GetMapping("/{paymentId}")
    public Response<PaymentResponse> getPayment(
            @AuthenticationPrincipal UUID merchantId,
            @PathVariable UUID paymentId) {

        PaymentIntent payment = paymentService.getPayment(
                merchantId,
                paymentId
        );

        return Response.success(PaymentResponse.from(payment));
    }
}


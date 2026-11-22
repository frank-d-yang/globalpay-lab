package com.globalpay.lab.payment.api.dto;

import java.util.List;

public record PaymentPageResponse(
        List<PaymentResponse> items,
        int page,
        int size,
        long total
) {
}


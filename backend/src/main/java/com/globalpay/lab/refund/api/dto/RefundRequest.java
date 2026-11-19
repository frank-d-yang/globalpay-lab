package com.globalpay.lab.refund.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RefundRequest(
        @NotNull
        UUID paymentId
) {
}

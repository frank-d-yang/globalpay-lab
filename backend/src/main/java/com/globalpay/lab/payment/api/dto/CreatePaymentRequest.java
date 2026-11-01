package com.globalpay.lab.payment.api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreatePaymentRequest(

        @NotNull
        @Positive
        @Digits(integer = 16, fraction = 8)
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String currency

) {
}


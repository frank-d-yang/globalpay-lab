package com.globalpay.lab.payment.application.port;

public record ProviderResult(
        ProviderOutcome outcome,
        String providerReference
) {
}

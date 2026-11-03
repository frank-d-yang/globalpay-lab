package com.globalpay.lab.payment.application.port;

import com.globalpay.lab.payment.domain.PaymentIntent;

public interface PaymentProvider {

    ProviderResult process(
            PaymentIntent payment,
            String operationId
    );

    ProviderResult getResult(String operationId);
}

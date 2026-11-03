package com.globalpay.lab.payment.infrastructure.provider;

import com.globalpay.lab.mockpsp.application.MockPaymentRequest;
import com.globalpay.lab.mockpsp.application.MockPaymentService;
import com.globalpay.lab.mockpsp.domain.ProviderOperation;
import com.globalpay.lab.payment.application.port.PaymentProvider;
import com.globalpay.lab.payment.application.port.ProviderOutcome;
import com.globalpay.lab.payment.application.port.ProviderResult;
import com.globalpay.lab.payment.domain.PaymentIntent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LocalMockPspAdapter implements PaymentProvider {

    @Autowired
    private MockPaymentService mockPaymentService;

    @Override
    public ProviderResult process(
            PaymentIntent payment,
            String operationId) {

        MockPaymentRequest request = new MockPaymentRequest(
                payment.getId(),
                operationId,
                payment.getAmount(),
                payment.getCurrency()
        );

        ProviderOperation operation =
                mockPaymentService.process(request);

        return toResult(operation);
    }

    @Override
    public ProviderResult getResult(String operationId) {

        ProviderOperation operation =
                mockPaymentService.getResult(operationId);

        if (operation == null) {
            return new ProviderResult(
                    ProviderOutcome.UNKNOWN,
                    null
            );
        }

        return toResult(operation);
    }

    private ProviderResult toResult(ProviderOperation operation) {

        return switch (operation.getStatus()) {

            case SUCCEEDED -> new ProviderResult(
                    ProviderOutcome.SUCCEEDED,
                    operation.getProviderReference()
            );

            case DECLINED -> new ProviderResult(
                    ProviderOutcome.DECLINED,
                    operation.getProviderReference()
            );

            case PROCESSING -> new ProviderResult(
                    ProviderOutcome.UNKNOWN,
                    null
            );
        };
    }
}


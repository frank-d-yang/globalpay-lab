package com.globalpay.lab.payment.api;

import com.globalpay.lab.payment.application.PaymentService;
import com.globalpay.lab.payment.domain.PaymentIntent;
import com.globalpay.lab.security.SecurityConfig;
import com.globalpay.lab.security.application.MerchantApiKeyService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private MerchantApiKeyService apiKeyService;

    private final UUID merchantId = UUID.randomUUID();
    private final Currency aud = Currency.getInstance("AUD");
    private final String rawKey = "test-api-key";

    @Test
    void shouldCreatePaymentSuccessfully() throws Exception {

        PaymentIntent payment = PaymentIntent.create(
                merchantId,
                new BigDecimal("100.00"),
                aud,
                "order-001"
        );

        when(apiKeyService.authenticate(rawKey))
                .thenReturn(merchantId);

        when(paymentService.createPayment(
                eq(merchantId),
                any(BigDecimal.class),
                eq(aud),
                eq("order-001")
        )).thenReturn(payment);

        String json = """
                {
                    "amount": 100.00,
                    "currency": "AUD"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/payments")
                                .header("X-API-Key", rawKey)
                                .header("Idempotency-Key", "order-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id")
                        .value(payment.getId().toString()))
                .andExpect(jsonPath("$.data.currency").value("AUD"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        verify(paymentService).createPayment(
                eq(merchantId),
                argThat(amount ->
                        amount.compareTo(new BigDecimal("100.00")) == 0),
                eq(aud),
                eq("order-001")
        );
    }

    @Test
    void shouldGetPaymentSuccessfully() throws Exception {

        PaymentIntent payment = PaymentIntent.create(
                merchantId,
                new BigDecimal("100.00"),
                aud,
                "order-001"
        );

        when(apiKeyService.authenticate(rawKey))
                .thenReturn(merchantId);

        when(paymentService.getPayment(
                merchantId,
                payment.getId()
        )).thenReturn(payment);

        mockMvc.perform(
                        get("/api/v1/payments/{id}", payment.getId())
                                .header("X-API-Key", rawKey)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id")
                        .value(payment.getId().toString()))
                .andExpect(jsonPath("$.data.amount").value(100.00));

        verify(paymentService).getPayment(
                merchantId,
                payment.getId()
        );
    }

    @Test
    void shouldRejectRequestWithoutApiKey() throws Exception {

        mockMvc.perform(
                        get("/api/v1/payments/{id}", UUID.randomUUID())
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService);
    }
}


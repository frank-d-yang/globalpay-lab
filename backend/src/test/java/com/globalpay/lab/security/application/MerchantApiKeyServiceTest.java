package com.globalpay.lab.security.application;

import com.globalpay.lab.merchant.application.MerchantService;
import com.globalpay.lab.merchant.domain.Merchant;
import com.globalpay.lab.merchant.domain.MerchantStatus;
import com.globalpay.lab.security.domain.ApiKeyStatus;
import com.globalpay.lab.security.domain.MerchantApiKey;
import com.globalpay.lab.security.infrastructure.MerchantApiKeyMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantApiKeyServiceTest {

    @Mock
    private MerchantService merchantService;

    @Mock
    private MerchantApiKeyMapper apiKeyMapper;

    @InjectMocks
    private MerchantApiKeyService apiKeyService;

    @Test
    void shouldIssueApiKey() throws Exception {

        UUID merchantId = UUID.randomUUID();

        Merchant merchant = new Merchant(
                merchantId,
                "Frank Commerce",
                MerchantStatus.ACTIVE,
                Instant.now()
        );

        when(merchantService.getMerchant(merchantId))
                .thenReturn(merchant);

        String rawKey = apiKeyService.issueKey(merchantId);

        ArgumentCaptor<MerchantApiKey> captor =
                ArgumentCaptor.forClass(MerchantApiKey.class);

        verify(apiKeyMapper).insert(captor.capture());

        MerchantApiKey saved = captor.getValue();

        assertEquals(merchantId, saved.getMerchantId());
        assertTrue(rawKey.startsWith(
                "gpl_test_" + saved.getId() + "."
        ));

        String secret = rawKey.substring(
                rawKey.indexOf('.') + 1
        );

        byte[] expectedHash =
                MessageDigest.getInstance("SHA-256")
                        .digest(secret.getBytes(
                                StandardCharsets.UTF_8
                        ));

        assertEquals(
                HexFormat.of().formatHex(expectedHash),
                saved.getKeyHash()
        );

        assertNotEquals(rawKey, saved.getKeyHash());
    }

    @Test
    void shouldRejectInactiveMerchant() {

        UUID merchantId = UUID.randomUUID();

        Merchant merchant = new Merchant(
                merchantId,
                "Frank Commerce",
                MerchantStatus.SUSPENDED,
                Instant.now()
        );

        when(merchantService.getMerchant(merchantId))
                .thenReturn(merchant);

        assertThrows(
                IllegalStateException.class,
                () -> apiKeyService.issueKey(merchantId)
        );

        verifyNoInteractions(apiKeyMapper);
    }

    @Test
    void shouldAuthenticateValidApiKey() {

        UUID merchantId = UUID.randomUUID();

        Merchant merchant = new Merchant(
                merchantId,
                "Frank Commerce",
                MerchantStatus.ACTIVE,
                Instant.now()
        );

        when(merchantService.getMerchant(merchantId))
                .thenReturn(merchant);

        // Issue an API key
        String rawKey = apiKeyService.issueKey(merchantId);

        // Capture the record that would be saved
        ArgumentCaptor<MerchantApiKey> captor =
                ArgumentCaptor.forClass(MerchantApiKey.class);

        verify(apiKeyMapper).insert(captor.capture());

        MerchantApiKey saved = captor.getValue();

        // Mock the database lookup
        when(apiKeyMapper.findById(saved.getId()))
                .thenReturn(saved);

        // Authenticate using the issued key
        UUID result = apiKeyService.authenticate(rawKey);

        assertEquals(merchantId, result);
    }


    @Test
    void shouldRejectInvalidSecret() {

        UUID merchantId = UUID.randomUUID();

        Merchant merchant = new Merchant(
                merchantId,
                "Frank Commerce",
                MerchantStatus.ACTIVE,
                Instant.now()
        );

        when(merchantService.getMerchant(merchantId))
                .thenReturn(merchant);

        String rawKey = apiKeyService.issueKey(merchantId);

        ArgumentCaptor<MerchantApiKey> captor =
                ArgumentCaptor.forClass(MerchantApiKey.class);

        verify(apiKeyMapper).insert(captor.capture());

        MerchantApiKey saved = captor.getValue();

        when(apiKeyMapper.findById(saved.getId()))
                .thenReturn(saved);

        // Correct Key ID, but incorrect Secret
        String invalidKey =
                "gpl_test_" + saved.getId() + "." + "A".repeat(43);

        assertThrows(
                BadCredentialsException.class,
                () -> apiKeyService.authenticate(invalidKey)
        );
    }

    @Test
    void shouldRevokeApiKey() {

        UUID keyId = UUID.randomUUID();

        when(apiKeyMapper.revoke(keyId))
                .thenReturn(1, 0);

        assertTrue(apiKeyService.revokeKey(keyId));

        // Repeated revocation should not update the record.
        assertFalse(apiKeyService.revokeKey(keyId));

        verify(apiKeyMapper, times(2)).revoke(keyId);
    }

    @Test
    void shouldRejectRevokedApiKey() {

        UUID merchantId = UUID.randomUUID();

        Merchant merchant = new Merchant(
                merchantId,
                "Frank Commerce",
                MerchantStatus.ACTIVE,
                Instant.now()
        );

        when(merchantService.getMerchant(merchantId))
                .thenReturn(merchant);

        String rawKey = apiKeyService.issueKey(merchantId);

        ArgumentCaptor<MerchantApiKey> captor =
                ArgumentCaptor.forClass(MerchantApiKey.class);

        verify(apiKeyMapper).insert(captor.capture());

        MerchantApiKey saved = captor.getValue();

        Instant createdAt = Instant.parse("2026-11-01T00:00:00Z");

        MerchantApiKey revoked = new MerchantApiKey(
                saved.getId(),
                saved.getMerchantId(),
                saved.getKeyHash(),
                ApiKeyStatus.REVOKED,
                createdAt,
                createdAt.plusSeconds(1)
        );

        when(apiKeyMapper.findById(saved.getId()))
                .thenReturn(revoked);

        assertThrows(
                BadCredentialsException.class,
                () -> apiKeyService.authenticate(rawKey)
        );
    }
}

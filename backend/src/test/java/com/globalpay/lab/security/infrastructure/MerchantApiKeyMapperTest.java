package com.globalpay.lab.security.infrastructure;

import com.globalpay.lab.merchant.domain.Merchant;
import com.globalpay.lab.merchant.domain.MerchantStatus;
import com.globalpay.lab.merchant.infrastructure.MerchantMapper;
import com.globalpay.lab.security.domain.ApiKeyStatus;
import com.globalpay.lab.security.domain.MerchantApiKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class MerchantApiKeyMapperTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private MerchantApiKeyMapper apiKeyMapper;

    @Autowired
    private MerchantMapper merchantMapper;

    private MerchantApiKey createApiKey() {

        Merchant merchant = new Merchant(
                UUID.randomUUID(),
                "Frank Commerce",
                MerchantStatus.ACTIVE,
                Instant.now()
        );

        merchantMapper.insert(merchant);

        // Generate a unique 64-character hex value for testing.
        String keyHash =
                UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", "");

        // Dummy SHA-256-shaped value for persistence testing only.
        return new MerchantApiKey(
                UUID.randomUUID(),
                merchant.getId(),
                keyHash,
                ApiKeyStatus.ACTIVE,
                Instant.now(),
                null
        );
    }

    @Test
    void shouldInsertAndFindApiKey() {

        MerchantApiKey apiKey = createApiKey();

        assertEquals(1, apiKeyMapper.insert(apiKey));

        MerchantApiKey saved =
                apiKeyMapper.findById(apiKey.getId());

        assertNotNull(saved);
        assertEquals(apiKey.getId(), saved.getId());
        assertEquals(apiKey.getMerchantId(), saved.getMerchantId());
        assertEquals(apiKey.getKeyHash(), saved.getKeyHash());
        assertEquals(ApiKeyStatus.ACTIVE, saved.getStatus());
        assertNull(saved.getRevokedAt());
    }

    @Test
    void shouldRevokeApiKey() {

        MerchantApiKey apiKey = createApiKey();
        apiKeyMapper.insert(apiKey);

        assertEquals(1, apiKeyMapper.revoke(apiKey.getId()));
        assertEquals(0, apiKeyMapper.revoke(apiKey.getId()));

        MerchantApiKey saved =
                apiKeyMapper.findById(apiKey.getId());

        assertEquals(ApiKeyStatus.REVOKED, saved.getStatus());
        assertNotNull(saved.getRevokedAt());
    }

}


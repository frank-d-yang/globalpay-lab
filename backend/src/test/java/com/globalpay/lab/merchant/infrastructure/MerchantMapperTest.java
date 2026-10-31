package com.globalpay.lab.merchant.infrastructure;

import com.globalpay.lab.merchant.domain.Merchant;
import com.globalpay.lab.merchant.domain.MerchantStatus;
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
public class MerchantMapperTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private MerchantMapper merchantMapper;

    @Test
    void shouldInsertAndFindMerchant() {

        UUID merchantId = UUID.randomUUID();

        Merchant merchant = new Merchant(
                merchantId,
                "Frank Commerce",
                MerchantStatus.ACTIVE,
                Instant.now()
        );

        assertEquals(1, merchantMapper.insert(merchant));

        Merchant saved = merchantMapper.findById(merchantId);

        assertNotNull(saved);
        assertEquals(merchantId, saved.getId());
        assertEquals("Frank Commerce", saved.getDisplayName());
        assertEquals(MerchantStatus.ACTIVE, saved.getStatus());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void shouldReturnNullWhenMerchantNotFound() {

        Merchant result =
                merchantMapper.findById(UUID.randomUUID());

        assertNull(result);
    }
}

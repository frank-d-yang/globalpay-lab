package com.globalpay.lab.merchant.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MerchantTest {
    @Test
    void shouldCreateActiveMerchant() {

        UUID id = UUID.randomUUID();

        Merchant merchant = new Merchant(
                id,
                "Frank Commerce",
                MerchantStatus.ACTIVE,
                Instant.now()
        );

        assertEquals(id, merchant.getId());
        assertEquals("Frank Commerce", merchant.getDisplayName());
        assertEquals(MerchantStatus.ACTIVE, merchant.getStatus());
    }

    @Test
    void shouldRejectBlankDisplayName() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new Merchant(
                        UUID.randomUUID(),
                        " ",
                        MerchantStatus.ACTIVE,
                        Instant.now()
                )
        );
    }
}

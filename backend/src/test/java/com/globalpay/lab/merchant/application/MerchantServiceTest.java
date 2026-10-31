
package com.globalpay.lab.merchant.application;

import com.globalpay.lab.common.exception.BusinessException;
import com.globalpay.lab.common.api.ResultCode;
import com.globalpay.lab.merchant.domain.Merchant;
import com.globalpay.lab.merchant.domain.MerchantStatus;
import com.globalpay.lab.merchant.infrastructure.MerchantMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantMapper merchantMapper;

    @InjectMocks
    private MerchantService merchantService;

    @Test
    void shouldCreateMerchant() {

        when(merchantMapper.insert(any(Merchant.class)))
                .thenReturn(1);

        Merchant merchant =
                merchantService.createMerchant("Frank Commerce");

        assertNotNull(merchant.getId());
        assertEquals("Frank Commerce", merchant.getDisplayName());
        assertEquals(MerchantStatus.ACTIVE, merchant.getStatus());
        assertNotNull(merchant.getCreatedAt());

        verify(merchantMapper).insert(merchant);
    }

    @Test
    void shouldRejectBlankDisplayName() {

        assertThrows(
                IllegalArgumentException.class,
                () -> merchantService.createMerchant(" ")
        );

        verifyNoInteractions(merchantMapper);
    }

    @Test
    void shouldGetMerchantById() {

        UUID id = UUID.randomUUID();

        Merchant merchant = new Merchant(
                id,
                "Frank Commerce",
                MerchantStatus.ACTIVE,
                java.time.Instant.now()
        );

        when(merchantMapper.findById(id))
                .thenReturn(merchant);

        Merchant result = merchantService.getMerchant(id);

        assertEquals(id, result.getId());
        assertEquals("Frank Commerce", result.getDisplayName());

        verify(merchantMapper).findById(id);
    }

    @Test
    void shouldThrowWhenMerchantNotFound() {

        UUID id = UUID.randomUUID();

        when(merchantMapper.findById(id))
                .thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> merchantService.getMerchant(id)
        );

        assertEquals(
                ResultCode.MERCHANT_NOT_FOUND,
                exception.getResultCode()
        );
    }
}

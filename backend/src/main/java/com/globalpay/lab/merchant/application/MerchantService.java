package com.globalpay.lab.merchant.application;

import com.globalpay.lab.common.api.ResultCode;
import com.globalpay.lab.common.exception.BusinessException;
import com.globalpay.lab.merchant.domain.Merchant;
import com.globalpay.lab.merchant.domain.MerchantStatus;
import com.globalpay.lab.merchant.infrastructure.MerchantMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class MerchantService {

    @Autowired
    private MerchantMapper merchantMapper;

    @Transactional
    public Merchant createMerchant(String displayName) {

        Merchant merchant = new Merchant(
                UUID.randomUUID(),
                displayName,
                MerchantStatus.ACTIVE,
                Instant.now()
        );

        merchantMapper.insert(merchant);

        return merchant;
    }

    public Merchant getMerchant(UUID id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Merchant id must not be null"
            );
        }

        Merchant merchant = merchantMapper.findById(id);

        if (merchant == null) {
            throw new BusinessException(
                    ResultCode.MERCHANT_NOT_FOUND
            );
        }

        return merchant;
    }
}

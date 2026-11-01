package com.globalpay.lab.security.infrastructure;

import com.globalpay.lab.security.domain.MerchantApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface MerchantApiKeyMapper {
    int insert(MerchantApiKey apiKey);

    MerchantApiKey findById(@Param("id") UUID id);

    int revoke(@Param("id") UUID id);
}

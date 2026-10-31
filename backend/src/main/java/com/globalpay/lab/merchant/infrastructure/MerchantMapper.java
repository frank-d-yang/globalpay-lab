package com.globalpay.lab.merchant.infrastructure;

import com.globalpay.lab.merchant.domain.Merchant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface MerchantMapper {

    int insert(Merchant merchant);

    Merchant findById(@Param("id") UUID id);

}

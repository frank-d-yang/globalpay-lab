package com.globalpay.lab.ledger.infrastructure;

import com.globalpay.lab.ledger.domain.LedgerAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface LedgerAccountMapper {

    int insert(LedgerAccount account);

    LedgerAccount findById(@Param("id") UUID id);

    List<LedgerAccount> findAll();

}

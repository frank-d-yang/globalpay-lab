package com.globalpay.lab.ledger.infrastructure;

import com.globalpay.lab.ledger.domain.AccountType;
import com.globalpay.lab.ledger.domain.LedgerAccount;
import com.globalpay.lab.ledger.domain.OwnerType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Mapper
public interface LedgerAccountMapper {

    int insert(LedgerAccount account);

    LedgerAccount findById(@Param("id") UUID id);

    List<LedgerAccount> findAll();

    BigDecimal sumDebitMinusCredit(@Param("id") UUID id);

    LedgerAccount findByOwnerAndCurrencyAndType(
            @Param("ownerType") OwnerType ownerType,
            @Param("ownerId") String ownerId,
            @Param("currency") Currency currency,
            @Param("accountType") AccountType accountType
    );

    int insertIfAbsent(LedgerAccount account);

}

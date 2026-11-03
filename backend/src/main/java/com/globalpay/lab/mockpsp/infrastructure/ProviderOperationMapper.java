package com.globalpay.lab.mockpsp.infrastructure;

import com.globalpay.lab.mockpsp.domain.ProviderOperation;
import com.globalpay.lab.mockpsp.domain.ProviderOperationStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProviderOperationMapper {

    int insertIfAbsent(ProviderOperation operation);

    ProviderOperation findByOperationId(
            @Param("operationId") String operationId
    );

    int updateStatus(
            @Param("operation") ProviderOperation operation,
            @Param("expectedStatus") ProviderOperationStatus expectedStatus
    );
}

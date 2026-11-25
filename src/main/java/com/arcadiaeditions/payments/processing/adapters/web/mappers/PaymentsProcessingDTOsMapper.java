package com.arcadiaeditions.payments.processing.adapters.web.mappers;

import com.arcadiaeditions.payments.processing.adapters.web.dtos.*;
import com.arcadiaeditions.payments.processing.core.domain.*;
import com.arcadiaeditions.payments.processing.core.inbound.dtos.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(uses = BaseMapper.class)
public interface PaymentsProcessingDTOsMapper {

    PaymentsProcessingDTOsMapper INSTANCE = Mappers.getMapper(PaymentsProcessingDTOsMapper.class);

    // request mappings
    GetPaymentStatusInput asGetPaymentStatusInput(GetPaymentStatusInputDTO dto);

    // response mappings

    GetPaymentStatusResultDTO asGetPaymentStatusResultDTO(GetPaymentStatusResult entity);
}

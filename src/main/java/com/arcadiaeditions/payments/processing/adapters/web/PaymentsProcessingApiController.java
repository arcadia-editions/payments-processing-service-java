package com.arcadiaeditions.payments.processing.adapters.web;

import com.arcadiaeditions.payments.processing.adapters.web.dtos.*;
import com.arcadiaeditions.payments.processing.adapters.web.mappers.*;
import com.arcadiaeditions.payments.processing.core.domain.*;
import com.arcadiaeditions.payments.processing.core.inbound.*;
import com.arcadiaeditions.payments.processing.core.inbound.dtos.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * REST controller for PaymentsProcessingApi.
 */
@RestController
@RequestMapping("/api")
public class PaymentsProcessingApiController implements PaymentsProcessingApi {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private NativeWebRequest request;

    private PaymentsProcessingService paymentsProcessingService;

    @Autowired
    public PaymentsProcessingApiController setPaymentsProcessingService(
            PaymentsProcessingService paymentsProcessingService) {
        this.paymentsProcessingService = paymentsProcessingService;
        return this;
    }

    private PaymentsProcessingDTOsMapper mapper = PaymentsProcessingDTOsMapper.INSTANCE;

    public PaymentsProcessingApiController(PaymentsProcessingService paymentsProcessingService) {

        this.paymentsProcessingService = paymentsProcessingService;
    }

    public Optional<NativeWebRequest> getRequest() {
        return Optional.ofNullable(request);
    }

    @Override
    public ResponseEntity<GetPaymentStatusResultDTO> getPaymentStatus(GetPaymentStatusInputDTO reqBody) {
        log.debug("REST request to getPaymentStatus: {}", reqBody);
        var input = mapper.asGetPaymentStatusInput(reqBody);
        var getPaymentStatusResult = paymentsProcessingService.getPaymentStatus(input);
        GetPaymentStatusResultDTO responseDTO = mapper.asGetPaymentStatusResultDTO(getPaymentStatusResult);
        return ResponseEntity.status(201).body(responseDTO);
    }

    protected Pageable pageOf(Integer page, Integer limit, List<String> sort) {
        Sort sortOrder = sort != null
                ? Sort.by(sort.stream()
                        .map(sortParam -> {
                            String[] parts = sortParam.split(":");
                            String property = parts[0];
                            Sort.Direction direction =
                                    parts.length > 1 ? Sort.Direction.fromString(parts[1]) : Sort.Direction.ASC;
                            return new Sort.Order(direction, property);
                        })
                        .toList())
                : Sort.unsorted();
        return PageRequest.of(page != null ? page : 0, limit != null ? limit : 10, sortOrder);
    }
}

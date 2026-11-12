package com.nexabank.api.mapper;

import com.nexabank.api.dto.paymentrequest.PaymentRequestResponse;
import com.nexabank.api.model.PaymentRequest;
import org.springframework.stereotype.Component;

/**
 * Mapper para transformaciones PaymentRequest → DTO.
 */
@Component
public class PaymentRequestMapper {

    public PaymentRequestResponse toResponse(PaymentRequest paymentRequest) {
        return PaymentRequestResponse.builder()
                .id(paymentRequest.getId())
                .requesterId(paymentRequest.getRequesterId())
                .requesterAccountId(paymentRequest.getRequesterAccountId())
                .payerPhone(paymentRequest.getPayerPhone())
                .amount(paymentRequest.getAmount())
                .currency(paymentRequest.getCurrency())
                .description(paymentRequest.getDescription())
                .status(paymentRequest.getStatus())
                .relatedTransferId(paymentRequest.getRelatedTransferId())
                .createdAt(paymentRequest.getCreatedAt())
                .expiresAt(paymentRequest.getExpiresAt())
                .build();
    }
}

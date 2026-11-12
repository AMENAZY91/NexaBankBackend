package com.nexabank.api.mapper;

import com.nexabank.api.dto.topup.TopupResponse;
import com.nexabank.api.model.Topup;
import org.springframework.stereotype.Component;

/**
 * Mapper para transformaciones Topup → DTO.
 */
@Component
public class TopupMapper {

    public TopupResponse toResponse(Topup topup) {
        return TopupResponse.builder()
                .id(topup.getId())
                .accountId(topup.getAccountId())
                .amount(topup.getAmount())
                .currency(topup.getCurrency())
                .source(topup.getSource())
                .status(topup.getStatus())
                .createdAt(topup.getCreatedAt())
                .completedAt(topup.getCompletedAt())
                .build();
    }
}

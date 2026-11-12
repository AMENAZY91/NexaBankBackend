package com.nexabank.api.mapper;

import com.nexabank.api.dto.withdrawal.WithdrawalResponse;
import com.nexabank.api.model.Withdrawal;
import org.springframework.stereotype.Component;

/**
 * Mapper para transformaciones Withdrawal → DTO.
 */
@Component
public class WithdrawalMapper {

    public WithdrawalResponse toResponse(Withdrawal withdrawal) {
        return WithdrawalResponse.builder()
                .id(withdrawal.getId())
                .accountId(withdrawal.getAccountId())
                .amount(withdrawal.getAmount())
                .currency(withdrawal.getCurrency())
                .destination(withdrawal.getDestination())
                .status(withdrawal.getStatus())
                .createdAt(withdrawal.getCreatedAt())
                .completedAt(withdrawal.getCompletedAt())
                .build();
    }
}

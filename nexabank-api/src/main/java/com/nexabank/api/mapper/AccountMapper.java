package com.nexabank.api.mapper;

import com.nexabank.api.dto.account.AccountResponse;
import com.nexabank.api.dto.account.CreateAccountRequest;
import com.nexabank.api.model.Account;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mapper para transformaciones Account ↔ DTO.
 */
@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .type(account.getType())
                .currency(account.getCurrency())
                .name(account.getName())
                .balance(account.getBalance())
                .status(account.getStatus())
                .build();
    }

    public Account fromCreateRequest(CreateAccountRequest request, String userId) {
        Instant now = Instant.now();
        return Account.builder()
                .userId(userId)
                .type(request.getType())
                .currency(request.getCurrency())
                .name(request.getName())
                .balance(BigDecimal.ZERO)
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}

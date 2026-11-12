package com.nexabank.api.dto.withdrawal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de respuesta para retiros (withdrawals).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalResponse {

    private String id;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String destination;
    private String status;
    private Instant createdAt;
    private Instant completedAt;
}

package com.nexabank.api.dto.withdrawal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de request para POST /api/v1/withdrawals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWithdrawalRequest {

    @NotBlank(message = "Account ID is required")
    private String accountId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotBlank(message = "Destination is required")
    @Pattern(regexp = "CARD|BANK_TRANSFER|CASH", message = "Destination must be one of: CARD, BANK_TRANSFER, CASH")
    private String destination;
}

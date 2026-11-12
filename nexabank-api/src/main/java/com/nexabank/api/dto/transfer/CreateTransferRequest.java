package com.nexabank.api.dto.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de request para POST /api/v1/transfers.
 *
 * <p>Soporta transferencias internacionales multi-moneda. El header {@code Idempotency-Key}
 * se extrae en el controller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransferRequest {

    @NotBlank(message = "Source account ID is required")
    private String sourceAccountId;

    @NotBlank(message = "Destination account ID is required")
    private String destinationAccountId;

    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Source currency is required")
    private String sourceCurrency;

    @NotBlank(message = "Destination currency is required")
    private String destinationCurrency;

    @NotBlank(message = "Currency is required")
    private String currency;

    private String description;
}

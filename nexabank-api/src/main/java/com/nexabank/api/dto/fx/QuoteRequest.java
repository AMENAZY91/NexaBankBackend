package com.nexabank.api.dto.fx;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request para obtener una cotización (quote) antes de ejecutar una transferencia.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteRequest {

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
}

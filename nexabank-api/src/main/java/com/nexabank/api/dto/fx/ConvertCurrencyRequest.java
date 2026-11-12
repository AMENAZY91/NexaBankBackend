package com.nexabank.api.dto.fx;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request para convertir un monto entre divisas (cotización previa).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvertCurrencyRequest {

    @NotBlank(message = "Source currency is required")
    private String sourceCurrency;

    @NotBlank(message = "Target currency is required")
    private String targetCurrency;

    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
}

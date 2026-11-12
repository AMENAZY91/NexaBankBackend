package com.nexabank.api.dto.fx;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Respuesta de conversión de moneda.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvertCurrencyResponse {

    private String sourceCurrency;
    private String targetCurrency;
    private BigDecimal sourceAmount;
    private BigDecimal targetAmount;
    private BigDecimal rate;
    private BigDecimal fee;
    private BigDecimal total;
}

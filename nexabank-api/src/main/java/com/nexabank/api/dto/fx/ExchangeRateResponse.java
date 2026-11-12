package com.nexabank.api.dto.fx;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de respuesta para una cotización de tipo de cambio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRateResponse {

    private String baseCurrency;
    private String targetCurrency;
    private BigDecimal rate;
    private Instant timestamp;
    private String source;
    private Instant expiresAt;
}

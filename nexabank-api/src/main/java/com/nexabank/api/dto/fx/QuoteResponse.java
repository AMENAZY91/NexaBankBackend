package com.nexabank.api.dto.fx;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Respuesta de cotización (quote) para una transferencia internacional.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteResponse {

    private String quoteId;
    private String sourceAccountId;
    private String destinationAccountId;
    private BigDecimal sourceAmount;
    private String sourceCurrency;
    private BigDecimal destinationAmount;
    private String destinationCurrency;
    private BigDecimal rate;
    private BigDecimal fee;
    private BigDecimal total;
    private Instant expiresAt;
    private String trackingNumber;
}

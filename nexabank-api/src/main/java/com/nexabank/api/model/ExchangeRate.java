package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code exchange_rates}.
 *
 * <p>Almacena la cotización de un par de divisas en un instante dado.
 * Se usa para cotizar y ejecutar transferencias internacionales con conversión de moneda.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate {

    private String id;
    private String baseCurrency;
    private String targetCurrency;
    private BigDecimal rate;
    private Instant timestamp;
    private String source;
    private Instant expiresAt;
}

package com.nexabank.api.service.provider;

import java.math.BigDecimal;

/**
 * Límites de gasto de una tarjeta.
 */
public record CardLimits(BigDecimal daily, BigDecimal monthly) {
}

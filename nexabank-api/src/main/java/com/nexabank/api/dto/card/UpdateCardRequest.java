package com.nexabank.api.dto.card;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de request para PATCH /api/v1/cards/{id}. Todos los campos son opcionales —
 * solo se aplica lo que venga presente; {@code status} solo acepta transiciones
 * ACTIVE↔BLOCKED (cancelar es un acto separado, vía DELETE).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCardRequest {

    @Pattern(regexp = "ACTIVE|BLOCKED", message = "Status must be one of: ACTIVE, BLOCKED")
    private String status;

    @Positive(message = "Daily limit must be positive")
    private BigDecimal dailyLimit;

    @Positive(message = "Monthly limit must be positive")
    private BigDecimal monthlyLimit;
}

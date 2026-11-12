package com.nexabank.api.dto.card;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de request para POST /api/v1/cards. Los límites son opcionales — si no se
 * envían, el servicio aplica los defaults configurados
 * ({@code app.card.default-daily-limit}/{@code default-monthly-limit}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCardRequest {

    @NotBlank(message = "Account ID is required")
    private String accountId;

    @Positive(message = "Daily limit must be positive")
    private BigDecimal dailyLimit;

    @Positive(message = "Monthly limit must be positive")
    private BigDecimal monthlyLimit;
}

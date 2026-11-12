package com.nexabank.api.dto.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de respuesta ligero para GET /api/v1/wallets/{id}/balance — evita transferir
 * el documento completo de la cuenta cuando el cliente solo necesita el saldo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {

    private BigDecimal balance;
    private String currency;
    private Instant updatedAt;
}

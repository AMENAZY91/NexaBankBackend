package com.nexabank.api.dto.account;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de respuesta alineado con el frontend (interface Account).
 *
 * <p>Campos usados por el frontend:
 * <pre>
 * { id, name, balance, currency, type, status }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private String id;
    private String name;
    private BigDecimal balance;
    private String currency;
    private String type;
    private String status;
}
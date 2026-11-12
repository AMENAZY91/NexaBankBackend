package com.nexabank.api.dto.account;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de request para POST /api/v1/accounts.
 *
 * <p>No incluye campo {@code balance} — el servidor lo inicializa a 0.
 * Nunca confiar en un balance enviado por el cliente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {

    @NotBlank(message = "Account type is required")
    private String type;

    @NotBlank(message = "Currency is required")
    private String currency;

    private String name;
}

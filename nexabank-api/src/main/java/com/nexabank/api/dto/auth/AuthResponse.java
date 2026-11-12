package com.nexabank.api.dto.auth;

import com.nexabank.api.dto.user.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response genérica para operaciones de auth.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String message;
    private String token;
    private Boolean requiresRegistration;
    private Boolean requiresPin;
    private Boolean authenticated;
    private UserResponse user;

    /**
     * Token de confirmación de corta vida (mejora #22), emitido tras un
     * {@code POST /api/v1/auth/pin/verify} exitoso. Debe enviarse en el header
     * {@code X-Confirmation-Token} en operaciones de dinero ({@code POST /api/v1/transfers},
     * {@code POST /api/v1/qr/pay}). {@code null} si la verificación de PIN falló.
     */
    private String confirmationToken;
}
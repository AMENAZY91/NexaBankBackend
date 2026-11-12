package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code confirmation_tokens}.
 *
 * <p>Token de confirmación de un solo uso y corta vida (mejora #22), emitido tras un
 * {@code POST /api/v1/auth/pin/verify} exitoso. Requerido en el header
 * {@code X-Confirmation-Token} para operaciones de mutación de dinero
 * ({@code POST /api/v1/transfers}, {@code POST /api/v1/qr/pay}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationToken {

    private String token;
    private String userId;
    private Instant expiresAt;

    @Builder.Default
    private boolean used = false;

    private Instant createdAt;
}

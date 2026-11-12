package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code idempotency_keys}.
 *
 * <p>Garantiza que operaciones financieras sensibles (como transferencias)
 * no se ejecuten duplicadas ante reintentos del cliente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey {

    private String id;
    private String key;
    private String userId;
    private String operation;
    private String requestHash;
    private String status;
    private String responseReference;
    private Instant createdAt;
    private Instant expiresAt;
}

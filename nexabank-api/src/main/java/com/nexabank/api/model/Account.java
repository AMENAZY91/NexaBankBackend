package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code accounts}.
 *
 * <p>Esquema:
 * <pre>
 * {
 *   "id": "ACC-000001",
 *   "userId": "USR-000001",
 *   "type": "SAVINGS",
 *   "name": "Cuenta principal",
 *   "currency": "COP",
 *   "balance": 1500000.00,
 *   "status": "ACTIVE",
 *   "createdAt": "Timestamp",
 *   "updatedAt": "Timestamp"
 * }
 * </pre>
 *
 * <p>El balance se modifica ÚNICAMENTE mediante operaciones financieras válidas.
 * Nunca confiar en un balance enviado por el cliente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    private String id;
    private String userId;
    private String type;
    private String name;
    private String currency;

    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Builder.Default
    private String status = "ACTIVE";

    private Instant createdAt;
    private Instant updatedAt;
}

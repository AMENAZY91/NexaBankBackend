package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code transactions}.
 *
 * <p>Tipos: INCOME, EXPENSE, TRANSFER
 * <p>Estados: PENDING, COMPLETED, FAILED, CANCELLED
 *
 * <p>Regla fundamental: nunca se debe borrar físicamente una transacción
 * financiera completada. Para reversar se crea una operación compensatoria.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    private String id;
    private String accountId;
    private String userId;
    private String type;
    private String category;
    private BigDecimal amount;
    private String currency;
    private String description;

    @Builder.Default
    private String status = "PENDING";

    private Instant timestamp;
    private Instant createdAt;
}

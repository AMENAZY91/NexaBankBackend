package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code topups}.
 *
 * <p>Estados: PENDING → COMPLETED/FAILED. {@code source} es una fuente mock
 * (CARD/BANK_TRANSFER/CASH) — no hay integración real con un proveedor de pagos
 * en esta fase (ver docs/IMPLEMENTATION_PLAN.md, Fase 4).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Topup {

    private String id;
    private String userId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String source;

    @Builder.Default
    private String status = "PENDING";

    private String idempotencyKey;
    private Instant createdAt;
    private Instant completedAt;
}

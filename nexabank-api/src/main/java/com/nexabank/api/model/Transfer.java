package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Entidad de dominio — documento Firestore {@code transfers}.
 *
 * <p>Transferencia internacional con soporte multi-moneda, comisiones,
 * tipo de cambio y ciclo de vida (PENDING → PROCESSING → COMPLETED/FAILED/CANCELLED).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transfer {

    private String id;
    private String sourceAccountId;
    private String destinationAccountId;
    private String sourceUserId;
    private String destinationUserId;

    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;
    private String sourceCurrency;
    private String destinationCurrency;

    /** Monto recibido en moneda de destino tras aplicar tipo de cambio y comisiones. */
    private BigDecimal destinationAmount;

    @Builder.Default
    private BigDecimal fee = BigDecimal.ZERO;
    private BigDecimal fxRate;
    private BigDecimal fxMargin;

    @Builder.Default
    private String status = "PENDING";

    private String idempotencyKey;
    private String description;
    private String trackingNumber;
    private Instant estimatedDeliveryDate;
    private Instant createdAt;
    private Instant completedAt;
    private Map<String, String> recipientInfo;
    private Map<String, String> senderInfo;
}

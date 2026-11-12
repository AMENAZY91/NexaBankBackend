package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code payment_requests}.
 *
 * <p>"Solicitar dinero a alguien" — compone sobre {@code TransferService} al pagarse
 * (ver {@code PaymentRequestService#pay}), no reimplementa movimiento de dinero.
 *
 * <p>Estados: PENDING → PAID/DECLINED/EXPIRED/CANCELLED.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    private String id;
    private String requesterId;
    private String requesterAccountId;
    private String payerUserId;
    private String payerPhone;
    private BigDecimal amount;
    private String currency;
    private String description;

    @Builder.Default
    private String status = "PENDING";

    private String relatedTransferId;
    private Instant createdAt;
    private Instant expiresAt;
}

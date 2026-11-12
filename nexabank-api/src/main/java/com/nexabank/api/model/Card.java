package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code cards}.
 *
 * <p>Nunca almacena un PAN/CVV completo — solo {@code last4} y {@code issuerToken}
 * (identificador opaco de correlación con {@code CardIssuer}, no un dato de pago en
 * sí). Estados: ACTIVE → BLOCKED (reversible) → CANCELLED (terminal).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Card {

    private String id;
    private String userId;
    private String accountId;
    private String brand;
    private String last4;
    private int expiryMonth;
    private int expiryYear;
    private String issuerToken;

    @Builder.Default
    private String status = "ACTIVE";

    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private Instant createdAt;
}

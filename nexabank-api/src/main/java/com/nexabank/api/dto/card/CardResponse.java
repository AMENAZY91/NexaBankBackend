package com.nexabank.api.dto.card;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de respuesta para tarjetas. Deliberadamente no incluye el token del emisor
 * ({@code issuerToken}) — el cliente no necesita esa correlación interna, solo
 * {@code last4} para identificar la tarjeta visualmente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardResponse {

    private String id;
    private String accountId;
    private String brand;
    private String last4;
    private int expiryMonth;
    private int expiryYear;
    private String status;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private Instant createdAt;
}

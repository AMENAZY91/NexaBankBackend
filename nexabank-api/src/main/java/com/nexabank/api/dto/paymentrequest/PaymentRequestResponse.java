package com.nexabank.api.dto.paymentrequest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de respuesta para solicitudes de pago.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestResponse {

    private String id;
    private String requesterId;
    private String requesterAccountId;
    private String payerPhone;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String status;
    private String relatedTransferId;
    private Instant createdAt;
    private Instant expiresAt;
}

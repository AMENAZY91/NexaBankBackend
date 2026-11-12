package com.nexabank.api.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * DTO de respuesta para transferencias.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {

    private String id;
    private String sourceAccountId;
    private String destinationAccountId;
    private String sourceUserId;
    private String destinationUserId;
    private BigDecimal amount;
    private String sourceCurrency;
    private String destinationCurrency;
    private BigDecimal destinationAmount;
    private String currency;
    private BigDecimal fee;
    private BigDecimal fxRate;
    private BigDecimal fxMargin;
    private String status;
    private String description;
    private String trackingNumber;
    private Instant estimatedDeliveryDate;
    private Instant createdAt;
    private Instant completedAt;
    private Map<String, String> recipientInfo;
    private Map<String, String> senderInfo;
}

package com.nexabank.api.dto.topup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de respuesta para recargas (topups).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopupResponse {

    private String id;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String source;
    private String status;
    private Instant createdAt;
    private Instant completedAt;
}

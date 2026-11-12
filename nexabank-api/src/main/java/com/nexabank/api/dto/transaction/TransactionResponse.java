package com.nexabank.api.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de respuesta alineado con el frontend (TransactionListProps).
 *
 * <p>Campos usados por el frontend:
 * <pre>
 * { id, name, note, amount, incoming, color }
 * </pre>
 * <p>El backend calcula {@code incoming} y {@code color} a partir del tipo/monto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private String id;
    private String name;
    private String note;
    private BigDecimal amount;
    private Boolean incoming;
    private String color;
}
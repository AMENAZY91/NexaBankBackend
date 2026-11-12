package com.nexabank.api.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para filtros de consulta de transacciones.
 *
 * <p>Filtros: accountId, type, category, status, from, to, page, size.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFilterRequest {

    private String accountId;
    private String type;
    private String category;
    private String status;
    private String from;
    private String to;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 20;
}

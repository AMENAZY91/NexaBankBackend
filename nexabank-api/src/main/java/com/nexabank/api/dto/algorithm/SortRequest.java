package com.nexabank.api.dto.algorithm;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de request para POST /api/v1/algorithms/sort.
 *
 * <p>Algoritmos disponibles: MERGE_SORT, QUICK_SORT.
 * Órdenes: ASC, DESC.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SortRequest {

    @NotBlank(message = "Algorithm is required")
    private String algorithm;

    @NotBlank(message = "Field is required")
    private String field;

    @Builder.Default
    private String order = "ASC";
}

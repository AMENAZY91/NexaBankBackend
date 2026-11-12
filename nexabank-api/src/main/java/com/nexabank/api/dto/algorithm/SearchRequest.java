package com.nexabank.api.dto.algorithm;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de request para POST /api/v1/algorithms/search.
 *
 * <p>Algoritmo: BINARY_SEARCH.
 * Pre-condición: los datos deben estar ordenados por el campo de búsqueda.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    @NotBlank(message = "Algorithm is required")
    private String algorithm;

    @NotBlank(message = "Field is required")
    private String field;

    @NotBlank(message = "Search value is required")
    private String value;
}

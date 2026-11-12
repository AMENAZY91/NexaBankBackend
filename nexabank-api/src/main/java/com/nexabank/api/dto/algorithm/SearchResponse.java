package com.nexabank.api.dto.algorithm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para POST /api/v1/algorithms/search.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    private String algorithm;
    private String target;
    private boolean found;
    private int position;
    private long executionTimeNanos;
}

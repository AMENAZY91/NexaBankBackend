package com.nexabank.api.dto.algorithm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de respuesta para POST /api/v1/algorithms/sort.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SortResponse {

    private String algorithm;
    private String field;
    private String order;
    private int inputSize;
    private long executionTimeNanos;
    private List<?> data;
}

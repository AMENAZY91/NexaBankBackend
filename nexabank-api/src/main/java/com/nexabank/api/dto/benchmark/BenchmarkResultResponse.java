package com.nexabank.api.dto.benchmark;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para benchmark results individuales.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkResultResponse {

    private String id;
    private String runId;
    private String algorithm;
    private String operation;
    private int inputSize;
    private double score;
    private double scoreError;
    private String unit;
}

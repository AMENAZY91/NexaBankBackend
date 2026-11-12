package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad de dominio — documento Firestore {@code benchmark_results}.
 *
 * <p>Resultado individual de un benchmark JMH para un algoritmo específico.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkResult {

    private String id;
    private String runId;
    private String algorithm;
    private String operation;
    private int inputSize;
    private double score;
    private double scoreError;
    private String unit;
}

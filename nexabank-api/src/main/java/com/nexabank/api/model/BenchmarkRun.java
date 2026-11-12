package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code benchmark_runs}.
 *
 * <p>Metadata de una ejecución JMH completa.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkRun {

    private String id;
    private String framework;
    private String javaVersion;
    private Instant executedAt;
    private String datasetSource;
    private int datasetSize;
    private String status;
}

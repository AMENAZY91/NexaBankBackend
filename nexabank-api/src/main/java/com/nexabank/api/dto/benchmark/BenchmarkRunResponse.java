package com.nexabank.api.dto.benchmark;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO de respuesta para benchmark runs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkRunResponse {

    private String id;
    private String framework;
    private String javaVersion;
    private Instant executedAt;
    private String datasetSource;
    private int datasetSize;
    private String status;
}

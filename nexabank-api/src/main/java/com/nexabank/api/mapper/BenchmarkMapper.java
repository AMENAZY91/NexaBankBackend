package com.nexabank.api.mapper;

import com.nexabank.api.dto.benchmark.BenchmarkResultResponse;
import com.nexabank.api.dto.benchmark.BenchmarkRunResponse;
import com.nexabank.api.model.BenchmarkResult;
import com.nexabank.api.model.BenchmarkRun;
import org.springframework.stereotype.Component;

/**
 * Mapper para transformaciones Benchmark ↔ DTO.
 */
@Component
public class BenchmarkMapper {

    public BenchmarkRunResponse toRunResponse(BenchmarkRun run) {
        return BenchmarkRunResponse.builder()
                .id(run.getId())
                .framework(run.getFramework())
                .javaVersion(run.getJavaVersion())
                .executedAt(run.getExecutedAt())
                .datasetSource(run.getDatasetSource())
                .datasetSize(run.getDatasetSize())
                .status(run.getStatus())
                .build();
    }

    public BenchmarkResultResponse toResultResponse(BenchmarkResult result) {
        return BenchmarkResultResponse.builder()
                .id(result.getId())
                .runId(result.getRunId())
                .algorithm(result.getAlgorithm())
                .operation(result.getOperation())
                .inputSize(result.getInputSize())
                .score(result.getScore())
                .scoreError(result.getScoreError())
                .unit(result.getUnit())
                .build();
    }
}

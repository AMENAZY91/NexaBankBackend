package com.nexabank.api.controller;

import com.nexabank.api.dto.benchmark.BenchmarkResultResponse;
import com.nexabank.api.dto.benchmark.BenchmarkRunResponse;
import com.nexabank.api.service.BenchmarkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/benchmarks")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @GetMapping
    public ResponseEntity<List<BenchmarkRunResponse>> getAllRuns() {
        List<BenchmarkRunResponse> runs = benchmarkService.getAllRuns();
        return ResponseEntity.ok(runs);
    }

    @GetMapping("/{runId}")
    public ResponseEntity<BenchmarkRunResponse> getRun(@PathVariable("runId") String runId) {
        BenchmarkRunResponse run = benchmarkService.getRunById(runId);
        return ResponseEntity.ok(run);
    }

    @GetMapping("/{runId}/results")
    public ResponseEntity<List<BenchmarkResultResponse>> getResults(@PathVariable("runId") String runId) {
        List<BenchmarkResultResponse> results = benchmarkService.getResultsByRun(runId);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/execute")
    public ResponseEntity<BenchmarkRunResponse> executeBenchmark(
            @RequestParam(value = "inputSize", defaultValue = "1000") int inputSize) {
        BenchmarkRunResponse run = benchmarkService.executeSyntheticBenchmark(inputSize);
        return ResponseEntity.status(HttpStatus.CREATED).body(run);
    }
}

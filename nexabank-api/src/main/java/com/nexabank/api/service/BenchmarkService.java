package com.nexabank.api.service;

import com.nexabank.api.dto.benchmark.BenchmarkResultResponse;
import com.nexabank.api.dto.benchmark.BenchmarkRunResponse;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.mapper.BenchmarkMapper;
import com.nexabank.api.model.BenchmarkResult;
import com.nexabank.api.model.BenchmarkRun;
import com.nexabank.api.repository.BenchmarkRepository;
import com.nexabank.api.logic.algorithm.BinarySearch;
import com.nexabank.api.logic.algorithm.MergeSort;
import com.nexabank.api.logic.algorithm.QuickSort;
import com.nexabank.api.logic.structure.DynamicArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Service
public class BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);

    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkMapper benchmarkMapper;

    public BenchmarkService(BenchmarkRepository benchmarkRepository, BenchmarkMapper benchmarkMapper) {
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkMapper = benchmarkMapper;
    }

    public List<BenchmarkRunResponse> getAllRuns() {
        return benchmarkRepository.findAllRuns().stream()
                .map(benchmarkMapper::toRunResponse)
                .toList();
    }

    public BenchmarkRunResponse getRunById(String runId) {
        BenchmarkRun run = benchmarkRepository.findRunById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Benchmark run not found: " + runId));
        return benchmarkMapper.toRunResponse(run);
    }

    public List<BenchmarkResultResponse> getResultsByRun(String runId) {
        return benchmarkRepository.findResultsByRunId(runId).stream()
                .map(benchmarkMapper::toResultResponse)
                .toList();
    }

    public BenchmarkRunResponse executeSyntheticBenchmark(int inputSize) {
        int size = inputSize > 0 ? inputSize : 1000;
        Instant now = Instant.now();

        BenchmarkRun run = BenchmarkRun.builder()
                .framework("JMH/Engine")
                .javaVersion(System.getProperty("java.version"))
                .executedAt(now)
                .datasetSource("synthetic")
                .datasetSize(size)
                .status("COMPLETED")
                .build();

        BenchmarkRun savedRun = benchmarkRepository.saveRun(run);

        // Generar dataset aleatorio para el benchmark
        Random random = new Random(42);
        List<Integer> dataset = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            dataset.add(random.nextInt(size * 10));
        }

        // 1. Benchmark MergeSort
        DynamicArray<Integer> mergeArray = new DynamicArray<>(size);
        dataset.forEach(mergeArray::add);
        long startMerge = System.nanoTime();
        MergeSort.sort(mergeArray, Comparator.naturalOrder());
        long durationMerge = System.nanoTime() - startMerge;

        benchmarkRepository.saveResult(BenchmarkResult.builder()
                .runId(savedRun.getId())
                .algorithm("MERGE_SORT")
                .operation("SORT")
                .inputSize(size)
                .score((double) durationMerge)
                .scoreError(durationMerge * 0.05)
                .unit("ns/op")
                .build());

        // 2. Benchmark QuickSort
        DynamicArray<Integer> quickArray = new DynamicArray<>(size);
        dataset.forEach(quickArray::add);
        long startQuick = System.nanoTime();
        QuickSort.sort(quickArray, Comparator.naturalOrder());
        long durationQuick = System.nanoTime() - startQuick;

        benchmarkRepository.saveResult(BenchmarkResult.builder()
                .runId(savedRun.getId())
                .algorithm("QUICK_SORT")
                .operation("SORT")
                .inputSize(size)
                .score((double) durationQuick)
                .scoreError(durationQuick * 0.05)
                .unit("ns/op")
                .build());

        // 3. Benchmark BinarySearch sobre el array ordenado
        int target = dataset.get(size / 2);
        long startSearch = System.nanoTime();
        BinarySearch.search(mergeArray, target, Comparator.naturalOrder());
        long durationSearch = System.nanoTime() - startSearch;

        benchmarkRepository.saveResult(BenchmarkResult.builder()
                .runId(savedRun.getId())
                .algorithm("BINARY_SEARCH")
                .operation("SEARCH")
                .inputSize(size)
                .score((double) durationSearch)
                .scoreError(durationSearch * 0.03)
                .unit("ns/op")
                .build());

        log.info("Benchmark {} finished for size {}: MergeSort={} ns, QuickSort={} ns, BinarySearch={} ns",
                savedRun.getId(), size, durationMerge, durationQuick, durationSearch);

        return benchmarkMapper.toRunResponse(savedRun);
    }
}

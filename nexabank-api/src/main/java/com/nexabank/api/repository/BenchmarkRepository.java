package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.nexabank.api.model.BenchmarkResult;
import com.nexabank.api.model.BenchmarkRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Repository para las colecciones {@code benchmark_runs} y {@code benchmark_results}.
 */
@Repository
public class BenchmarkRepository {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRepository.class);
    private static final String RUNS_COLLECTION = "benchmark_runs";
    private static final String RESULTS_COLLECTION = "benchmark_results";

    private final Firestore firestore;

    public BenchmarkRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    // =========================================================================
    // Benchmark Runs
    // =========================================================================

    public List<BenchmarkRun> findAllRuns() {
        try {
            QuerySnapshot query = firestore.collection(RUNS_COLLECTION)
                    .orderBy("executedAt", Query.Direction.DESCENDING)
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToRun)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public Optional<BenchmarkRun> findRunById(String runId) {
        try {
            DocumentSnapshot doc = firestore.collection(RUNS_COLLECTION).document(runId).get().get();
            if (doc.exists()) {
                return Optional.of(documentToRun(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public BenchmarkRun saveRun(BenchmarkRun run) {
        try {
            if (run.getId() == null) {
                run.setId("RUN-" + java.time.LocalDate.now().toString().replace("-", "") + "-" +
                        String.format("%03d", (int) (Math.random() * 999) + 1));
            }
            firestore.collection(RUNS_COLLECTION).document(run.getId())
                    .set(runToMap(run)).get();
            return run;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    // =========================================================================
    // Benchmark Results
    // =========================================================================

    public List<BenchmarkResult> findResultsByRunId(String runId) {
        try {
            QuerySnapshot query = firestore.collection(RESULTS_COLLECTION)
                    .whereEqualTo("runId", runId)
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToResult)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public BenchmarkResult saveResult(BenchmarkResult result) {
        try {
            if (result.getId() == null) {
                result.setId("RESULT-" + String.format("%06d", (int) (Math.random() * 999999) + 1));
            }
            firestore.collection(RESULTS_COLLECTION).document(result.getId())
                    .set(resultToMap(result)).get();
            return result;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    // =========================================================================
    // Mappers
    // =========================================================================

    private BenchmarkRun documentToRun(DocumentSnapshot doc) {
        return BenchmarkRun.builder()
                .id(doc.getString("id"))
                .framework(doc.getString("framework"))
                .javaVersion(doc.getString("javaVersion"))
                .executedAt(toInstant(doc.getTimestamp("executedAt")))
                .datasetSource(doc.getString("datasetSource"))
                .datasetSize(doc.getLong("datasetSize") != null ? doc.getLong("datasetSize").intValue() : 0)
                .status(doc.getString("status"))
                .build();
    }

    private Map<String, Object> runToMap(BenchmarkRun run) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", run.getId());
        map.put("framework", run.getFramework());
        map.put("javaVersion", run.getJavaVersion());
        map.put("executedAt", run.getExecutedAt() != null ? Timestamp.ofTimeSecondsAndNanos(run.getExecutedAt().getEpochSecond(), run.getExecutedAt().getNano()) : Timestamp.now());
        map.put("datasetSource", run.getDatasetSource());
        map.put("datasetSize", run.getDatasetSize());
        map.put("status", run.getStatus());
        return map;
    }

    private BenchmarkResult documentToResult(DocumentSnapshot doc) {
        return BenchmarkResult.builder()
                .id(doc.getString("id"))
                .runId(doc.getString("runId"))
                .algorithm(doc.getString("algorithm"))
                .operation(doc.getString("operation"))
                .inputSize(doc.getLong("inputSize") != null ? doc.getLong("inputSize").intValue() : 0)
                .score(doc.getDouble("score") != null ? doc.getDouble("score") : 0.0)
                .scoreError(doc.getDouble("scoreError") != null ? doc.getDouble("scoreError") : 0.0)
                .unit(doc.getString("unit"))
                .build();
    }

    private Map<String, Object> resultToMap(BenchmarkResult r) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", r.getId());
        map.put("runId", r.getRunId());
        map.put("algorithm", r.getAlgorithm());
        map.put("operation", r.getOperation());
        map.put("inputSize", r.getInputSize());
        map.put("score", r.getScore());
        map.put("scoreError", r.getScoreError());
        map.put("unit", r.getUnit());
        return map;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

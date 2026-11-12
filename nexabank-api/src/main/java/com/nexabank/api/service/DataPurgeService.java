package com.nexabank.api.service;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.nexabank.api.repository.BenchmarkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Servicio para purgar (borrar) todos los documentos de las colecciones de Firestore.
 *
 * <p>No elimina las colecciones, solo los documentos dentro de ellas, de modo que
 * la estructura/referencias de documento se mantiene vacía.
 */
@Service
public class DataPurgeService {

    private static final Logger log = LoggerFactory.getLogger(DataPurgeService.class);

    private static final String[] COLLECTIONS = {
            "users",
            "accounts",
            "transactions",
            "transfers",
            "beneficiaries",
            "audit_logs",
            "benchmark_runs",
            "benchmark_results",
            "idempotency_keys",
            "exchange_rates"
    };

    private final Firestore firestore;
    private final BenchmarkRepository benchmarkRepository;

    public DataPurgeService(Firestore firestore,
                            BenchmarkRepository benchmarkRepository) {
        this.firestore = firestore;
        this.benchmarkRepository = benchmarkRepository;
    }

    public Map<String, Object> purgeDatabase() {
        log.info("Starting database purge...");

        Map<String, Integer> deleted = new java.util.LinkedHashMap<>();
        for (String collection : COLLECTIONS) {
            deleted.put(collection, deleteAllDocuments(collection));
        }

        log.info("Database purge completed: {}", deleted);

        return Map.of(
                "status", "SUCCESS",
                "message", "All documents purged from Firestore. Collections kept empty (structure preserved).",
                "deleted", deleted
        );
    }

    private int deleteAllDocuments(String collection) {
        try {
            QuerySnapshot snapshot = firestore.collection(collection).get().get();
            List<QueryDocumentSnapshot> docs = snapshot.getDocuments();
            int count = docs.size();
            for (QueryDocumentSnapshot doc : docs) {
                doc.getReference().delete().get();
            }
            log.debug("Purged {} documents from collection '{}'", count, collection);
            return count;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Error purging collection '{}': {}", collection, e.getMessage());
            throw new RuntimeException("Error purging collection '" + collection + "'", e);
        }
    }
}

package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.nexabank.api.model.IdempotencyKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Repository para la colección {@code idempotency_keys} de Firestore.
 */
@Repository
public class IdempotencyRepository {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyRepository.class);
    private static final String COLLECTION = "idempotency_keys";

    private final Firestore firestore;

    public IdempotencyRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<IdempotencyKey> findByKey(String key) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("key", key)
                    .limit(1)
                    .get().get();
            if (!query.isEmpty()) {
                return Optional.of(documentToIdempotencyKey(query.getDocuments().get(0)));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public IdempotencyKey save(IdempotencyKey idempotencyKey) {
        try {
            if (idempotencyKey.getId() == null) {
                idempotencyKey.setId(generateId());
            }
            firestore.collection(COLLECTION).document(idempotencyKey.getId())
                    .set(idempotencyKeyToMap(idempotencyKey)).get();
            return idempotencyKey;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    private String generateId() {
        String id;
        int attempts = 0;
        do {
            id = "IDEMP-" + String.format("%06d", (int) (Math.random() * 999999) + 1);
            attempts++;
        } while (exists(id) && attempts < 10);
        return id;
    }

    private boolean exists(String id) {
        try {
            return firestore.collection(COLLECTION).document(id).get().get().exists();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public boolean isExpired(String key) {
        Optional<IdempotencyKey> existing = findByKey(key);
        if (existing.isEmpty()) return true;
        IdempotencyKey ik = existing.get();
        return ik.getExpiresAt() != null && ik.getExpiresAt().isBefore(Instant.now());
    }

    private IdempotencyKey documentToIdempotencyKey(DocumentSnapshot doc) {
        return IdempotencyKey.builder()
                .id(doc.getString("id"))
                .key(doc.getString("key"))
                .userId(doc.getString("userId"))
                .operation(doc.getString("operation"))
                .requestHash(doc.getString("requestHash"))
                .status(doc.getString("status"))
                .responseReference(doc.getString("responseReference"))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .expiresAt(toInstant(doc.getTimestamp("expiresAt")))
                .build();
    }

    private Map<String, Object> idempotencyKeyToMap(IdempotencyKey ik) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", ik.getId());
        map.put("key", ik.getKey());
        map.put("userId", ik.getUserId());
        map.put("operation", ik.getOperation());
        map.put("requestHash", ik.getRequestHash() != null ? ik.getRequestHash() : "");
        map.put("status", ik.getStatus());
        map.put("responseReference", ik.getResponseReference() != null ? ik.getResponseReference() : "");
        map.put("createdAt", ik.getCreatedAt() != null ? Timestamp.ofTimeSecondsAndNanos(ik.getCreatedAt().getEpochSecond(), ik.getCreatedAt().getNano()) : Timestamp.now());
        if (ik.getExpiresAt() != null) {
            map.put("expiresAt", Timestamp.ofTimeSecondsAndNanos(ik.getExpiresAt().getEpochSecond(), ik.getExpiresAt().getNano()));
        }
        return map;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.nexabank.api.model.Topup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Repository para la colección {@code topups} de Firestore.
 */
@Repository
public class TopupRepository {

    private static final Logger log = LoggerFactory.getLogger(TopupRepository.class);
    private static final String COLLECTION = "topups";

    private final Firestore firestore;

    public TopupRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<Topup> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(documentToTopup(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding topup: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public List<Topup> findByUserId(String userId) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("userId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToTopup)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding topups for user: {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public Topup save(Topup topup) {
        try {
            if (topup.getId() == null) {
                topup.setId(generateId());
            }
            firestore.collection(COLLECTION).document(topup.getId())
                    .set(topupToMap(topup)).get();
            log.debug("Saved topup: {}", topup.getId());
            return topup;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving topup: {}", topup.getId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    private String generateId() {
        return "TOP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Topup documentToTopup(DocumentSnapshot doc) {
        return Topup.builder()
                .id(doc.getString("id"))
                .userId(doc.getString("userId"))
                .accountId(doc.getString("accountId"))
                .amount(readAmount(doc))
                .currency(doc.getString("currency"))
                .source(doc.getString("source"))
                .status(doc.getString("status"))
                .idempotencyKey(doc.getString("idempotencyKey"))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .completedAt(toInstant(doc.getTimestamp("completedAt")))
                .build();
    }

    private Map<String, Object> topupToMap(Topup topup) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", topup.getId());
        map.put("userId", topup.getUserId());
        map.put("accountId", topup.getAccountId());
        map.put("amount", topup.getAmount().toPlainString());
        map.put("currency", topup.getCurrency());
        map.put("source", topup.getSource());
        map.put("status", topup.getStatus());
        map.put("idempotencyKey", topup.getIdempotencyKey() != null ? topup.getIdempotencyKey() : "");
        map.put("createdAt", topup.getCreatedAt() != null
                ? Timestamp.ofTimeSecondsAndNanos(topup.getCreatedAt().getEpochSecond(), topup.getCreatedAt().getNano())
                : Timestamp.now());
        if (topup.getCompletedAt() != null) {
            map.put("completedAt", Timestamp.ofTimeSecondsAndNanos(topup.getCompletedAt().getEpochSecond(), topup.getCompletedAt().getNano()));
        }
        return map;
    }

    private BigDecimal readAmount(DocumentSnapshot doc) {
        Object raw = doc.get("amount");
        if (raw == null) return BigDecimal.ZERO;
        if (raw instanceof String s) return new BigDecimal(s);
        if (raw instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

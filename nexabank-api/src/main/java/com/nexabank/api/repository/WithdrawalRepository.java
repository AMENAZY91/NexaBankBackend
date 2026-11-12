package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.nexabank.api.model.Withdrawal;
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
 * Repository para la colección {@code withdrawals} de Firestore.
 */
@Repository
public class WithdrawalRepository {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalRepository.class);
    private static final String COLLECTION = "withdrawals";

    private final Firestore firestore;

    public WithdrawalRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<Withdrawal> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(documentToWithdrawal(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding withdrawal: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public List<Withdrawal> findByUserId(String userId) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("userId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToWithdrawal)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding withdrawals for user: {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public Withdrawal save(Withdrawal withdrawal) {
        try {
            if (withdrawal.getId() == null) {
                withdrawal.setId(generateId());
            }
            firestore.collection(COLLECTION).document(withdrawal.getId())
                    .set(withdrawalToMap(withdrawal)).get();
            log.debug("Saved withdrawal: {}", withdrawal.getId());
            return withdrawal;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving withdrawal: {}", withdrawal.getId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    private String generateId() {
        return "WDR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Withdrawal documentToWithdrawal(DocumentSnapshot doc) {
        return Withdrawal.builder()
                .id(doc.getString("id"))
                .userId(doc.getString("userId"))
                .accountId(doc.getString("accountId"))
                .amount(readAmount(doc))
                .currency(doc.getString("currency"))
                .destination(doc.getString("destination"))
                .status(doc.getString("status"))
                .idempotencyKey(doc.getString("idempotencyKey"))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .completedAt(toInstant(doc.getTimestamp("completedAt")))
                .build();
    }

    private Map<String, Object> withdrawalToMap(Withdrawal withdrawal) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", withdrawal.getId());
        map.put("userId", withdrawal.getUserId());
        map.put("accountId", withdrawal.getAccountId());
        map.put("amount", withdrawal.getAmount().toPlainString());
        map.put("currency", withdrawal.getCurrency());
        map.put("destination", withdrawal.getDestination());
        map.put("status", withdrawal.getStatus());
        map.put("idempotencyKey", withdrawal.getIdempotencyKey() != null ? withdrawal.getIdempotencyKey() : "");
        map.put("createdAt", withdrawal.getCreatedAt() != null
                ? Timestamp.ofTimeSecondsAndNanos(withdrawal.getCreatedAt().getEpochSecond(), withdrawal.getCreatedAt().getNano())
                : Timestamp.now());
        if (withdrawal.getCompletedAt() != null) {
            map.put("completedAt", Timestamp.ofTimeSecondsAndNanos(withdrawal.getCompletedAt().getEpochSecond(), withdrawal.getCompletedAt().getNano()));
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

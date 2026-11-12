package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.nexabank.api.model.Card;
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
 * Repository para la colección {@code cards} de Firestore.
 */
@Repository
public class CardRepository {

    private static final Logger log = LoggerFactory.getLogger(CardRepository.class);
    private static final String COLLECTION = "cards";

    private final Firestore firestore;

    public CardRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<Card> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(documentToCard(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding card: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public List<Card> findByUserId(String userId) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("userId", userId)
                    .get().get();
            return query.getDocuments().stream().map(this::documentToCard).toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding cards for user: {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public Card save(Card card) {
        try {
            if (card.getId() == null) {
                card.setId(generateId());
            }
            firestore.collection(COLLECTION).document(card.getId())
                    .set(cardToMap(card)).get();
            log.debug("Saved card: {}", card.getId());
            return card;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving card: {}", card.getId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    private String generateId() {
        return "CARD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Card documentToCard(DocumentSnapshot doc) {
        return Card.builder()
                .id(doc.getString("id"))
                .userId(doc.getString("userId"))
                .accountId(doc.getString("accountId"))
                .brand(doc.getString("brand"))
                .last4(doc.getString("last4"))
                .expiryMonth(doc.getLong("expiryMonth") != null ? doc.getLong("expiryMonth").intValue() : 0)
                .expiryYear(doc.getLong("expiryYear") != null ? doc.getLong("expiryYear").intValue() : 0)
                .issuerToken(doc.getString("issuerToken"))
                .status(doc.getString("status"))
                .dailyLimit(readDecimal(doc, "dailyLimit"))
                .monthlyLimit(readDecimal(doc, "monthlyLimit"))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .build();
    }

    private Map<String, Object> cardToMap(Card card) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", card.getId());
        map.put("userId", card.getUserId());
        map.put("accountId", card.getAccountId());
        map.put("brand", card.getBrand());
        map.put("last4", card.getLast4());
        map.put("expiryMonth", card.getExpiryMonth());
        map.put("expiryYear", card.getExpiryYear());
        map.put("issuerToken", card.getIssuerToken());
        map.put("status", card.getStatus());
        map.put("dailyLimit", card.getDailyLimit() != null ? card.getDailyLimit().toPlainString() : "");
        map.put("monthlyLimit", card.getMonthlyLimit() != null ? card.getMonthlyLimit().toPlainString() : "");
        map.put("createdAt", card.getCreatedAt() != null
                ? Timestamp.ofTimeSecondsAndNanos(card.getCreatedAt().getEpochSecond(), card.getCreatedAt().getNano())
                : Timestamp.now());
        return map;
    }

    private BigDecimal readDecimal(DocumentSnapshot doc, String field) {
        Object raw = doc.get(field);
        if (raw == null) return null;
        if (raw instanceof String s) return s.isBlank() ? null : new BigDecimal(s);
        if (raw instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return null;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.nexabank.api.model.ConfirmationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Repository para la colección {@code confirmation_tokens} de Firestore (mejora #22).
 */
@Repository
public class ConfirmationTokenRepository {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationTokenRepository.class);
    private static final String COLLECTION = "confirmation_tokens";

    private final Firestore firestore;

    public ConfirmationTokenRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<ConfirmationToken> findByToken(String token) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(token).get().get();
            if (doc.exists()) {
                return Optional.of(documentToToken(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding confirmation token", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public ConfirmationToken save(ConfirmationToken confirmationToken) {
        try {
            firestore.collection(COLLECTION).document(confirmationToken.getToken())
                    .set(tokenToMap(confirmationToken)).get();
            return confirmationToken;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving confirmation token", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    /** Marca el token como usado (single-use). */
    public void markUsed(String token) {
        try {
            firestore.collection(COLLECTION).document(token).update(Map.of("used", true)).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error marking confirmation token as used", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    private ConfirmationToken documentToToken(DocumentSnapshot doc) {
        return ConfirmationToken.builder()
                .token(doc.getString("token"))
                .userId(doc.getString("userId"))
                .expiresAt(toInstant(doc.getTimestamp("expiresAt")))
                .used(Boolean.TRUE.equals(doc.getBoolean("used")))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .build();
    }

    private Map<String, Object> tokenToMap(ConfirmationToken ct) {
        Map<String, Object> map = new HashMap<>();
        map.put("token", ct.getToken());
        map.put("userId", ct.getUserId());
        map.put("expiresAt", Timestamp.ofTimeSecondsAndNanos(ct.getExpiresAt().getEpochSecond(), ct.getExpiresAt().getNano()));
        map.put("used", ct.isUsed());
        map.put("createdAt", ct.getCreatedAt() != null
                ? Timestamp.ofTimeSecondsAndNanos(ct.getCreatedAt().getEpochSecond(), ct.getCreatedAt().getNano())
                : Timestamp.now());
        return map;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

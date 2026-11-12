package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.nexabank.api.model.Beneficiary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Repository para la colección {@code beneficiaries} de Firestore.
 */
@Repository
public class BeneficiaryRepository {

    private static final Logger log = LoggerFactory.getLogger(BeneficiaryRepository.class);
    private static final String COLLECTION = "beneficiaries";

    private final Firestore firestore;

    public BeneficiaryRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<Beneficiary> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(documentToBeneficiary(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public List<Beneficiary> findByUserId(String userId) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("userId", userId)
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToBeneficiary)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public Beneficiary save(Beneficiary beneficiary) {
        try {
            if (beneficiary.getId() == null) {
                beneficiary.setId(generateId());
            }
            firestore.collection(COLLECTION).document(beneficiary.getId())
                    .set(beneficiaryToMap(beneficiary)).get();
            return beneficiary;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    public void delete(String id) {
        try {
            firestore.collection(COLLECTION).document(id).delete().get();
            log.debug("Deleted beneficiary: {}", id);
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error deleting from Firestore", e);
        }
    }

    private String generateId() {
        return "BEN-" + String.format("%06d", (int) (Math.random() * 999999) + 1);
    }

    private Beneficiary documentToBeneficiary(DocumentSnapshot doc) {
        return Beneficiary.builder()
                .id(doc.getString("id"))
                .userId(doc.getString("userId"))
                .name(doc.getString("name"))
                .alias(doc.getString("alias"))
                .accountReference(doc.getString("accountReference"))
                .status(doc.getString("status"))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .updatedAt(toInstant(doc.getTimestamp("updatedAt")))
                .build();
    }

    private Map<String, Object> beneficiaryToMap(Beneficiary b) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", b.getId());
        map.put("userId", b.getUserId());
        map.put("name", b.getName());
        map.put("alias", b.getAlias() != null ? b.getAlias() : "");
        map.put("accountReference", b.getAccountReference());
        map.put("status", b.getStatus());
        map.put("createdAt", b.getCreatedAt() != null ? Timestamp.ofTimeSecondsAndNanos(b.getCreatedAt().getEpochSecond(), b.getCreatedAt().getNano()) : Timestamp.now());
        map.put("updatedAt", Timestamp.now());
        return map;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

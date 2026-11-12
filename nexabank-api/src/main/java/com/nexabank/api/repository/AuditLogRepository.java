package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.nexabank.api.model.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Repository para la colección {@code audit_logs} de Firestore.
 *
 * <p>No almacenar secretos ni tokens en metadata.
 */
@Repository
public class AuditLogRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditLogRepository.class);
    private static final String COLLECTION = "audit_logs";

    private final Firestore firestore;

    public AuditLogRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    @SuppressWarnings("unchecked")
    public List<AuditLog> findByUserId(String userId) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("userId", userId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToAuditLog)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public AuditLog save(AuditLog auditLog) {
        try {
            if (auditLog.getId() == null) {
                auditLog.setId("AUD-" + String.format("%06d", (int) (Math.random() * 999999) + 1));
            }
            if (auditLog.getTimestamp() == null) {
                auditLog.setTimestamp(Instant.now());
            }
            firestore.collection(COLLECTION).document(auditLog.getId())
                    .set(auditLogToMap(auditLog)).get();
            log.debug("Saved audit log: {} - {}", auditLog.getId(), auditLog.getAction());
            return auditLog;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    public List<AuditLog> findAll() {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToAuditLog)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public long count() {
        try {
            return firestore.collection(COLLECTION).get().get().size();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    @SuppressWarnings("unchecked")
    private AuditLog documentToAuditLog(DocumentSnapshot doc) {
        return AuditLog.builder()
                .id(doc.getString("id"))
                .userId(doc.getString("userId"))
                .action(doc.getString("action"))
                .resourceType(doc.getString("resourceType"))
                .resourceId(doc.getString("resourceId"))
                .status(doc.getString("status"))
                .timestamp(toInstant(doc.getTimestamp("timestamp")))
                .metadata(doc.get("metadata") != null ? (Map<String, Object>) doc.get("metadata") : Map.of())
                .build();
    }

    private Map<String, Object> auditLogToMap(AuditLog al) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", al.getId());
        map.put("userId", al.getUserId());
        map.put("action", al.getAction());
        map.put("resourceType", al.getResourceType());
        map.put("resourceId", al.getResourceId());
        map.put("status", al.getStatus());
        map.put("timestamp", Timestamp.ofTimeSecondsAndNanos(al.getTimestamp().getEpochSecond(), al.getTimestamp().getNano()));
        map.put("metadata", al.getMetadata() != null ? al.getMetadata() : Map.of());
        return map;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

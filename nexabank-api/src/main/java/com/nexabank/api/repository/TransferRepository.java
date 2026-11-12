package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.nexabank.api.model.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Repository para la colección {@code transfers} de Firestore.
 */
@Repository
public class TransferRepository {

    private static final Logger log = LoggerFactory.getLogger(TransferRepository.class);
    private static final String COLLECTION = "transfers";

    private final Firestore firestore;

    public TransferRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<Transfer> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(documentToTransfer(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding transfer: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    /**
     * Transferencias donde el usuario es origen O destino — una transferencia tiene dos
     * partes, así que consultar solo {@code sourceUserId} (como hacía esta versión antes)
     * dejaba invisibles todas las transferencias recibidas. Se combinan ambas consultas
     * y se deduplica por ID (una transferencia entre dos cuentas propias del mismo
     * usuario calificaría para ambas).
     */
    public List<Transfer> findByUserId(String userId) {
        try {
            QuerySnapshot sentQuery = firestore.collection(COLLECTION)
                    .whereEqualTo("sourceUserId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get().get();
            QuerySnapshot receivedQuery = firestore.collection(COLLECTION)
                    .whereEqualTo("destinationUserId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get().get();

            Map<String, Transfer> merged = new LinkedHashMap<>();
            sentQuery.getDocuments().forEach(doc -> merged.put(doc.getId(), documentToTransfer(doc)));
            receivedQuery.getDocuments().forEach(doc -> merged.put(doc.getId(), documentToTransfer(doc)));

            return merged.values().stream()
                    .sorted(Comparator.comparing(Transfer::getCreatedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .reversed())
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding transfers for user: {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public Transfer save(Transfer transfer) {
        try {
            if (transfer.getId() == null) {
                transfer.setId(generateId());
            }
            firestore.collection(COLLECTION).document(transfer.getId())
                    .set(transferToMap(transfer)).get();
            log.debug("Saved transfer: {}", transfer.getId());
            return transfer;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving transfer: {}", transfer.getId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    private String generateId() {
        String id;
        int attempts = 0;
        do {
            id = "TRF-" + String.format("%06d", (int) (Math.random() * 999999) + 1);
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

    private Transfer documentToTransfer(DocumentSnapshot doc) {
        return Transfer.builder()
                .id(doc.getString("id"))
                .sourceAccountId(doc.getString("sourceAccountId"))
                .destinationAccountId(doc.getString("destinationAccountId"))
                .sourceUserId(doc.getString("sourceUserId"))
                .destinationUserId(doc.getString("destinationUserId"))
                .amount(readAmount(doc))
                .sourceCurrency(doc.getString("sourceCurrency"))
                .destinationCurrency(doc.getString("destinationCurrency"))
                .status(doc.getString("status"))
                .idempotencyKey(doc.getString("idempotencyKey"))
                .description(doc.getString("description"))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .completedAt(toInstant(doc.getTimestamp("completedAt")))
                .build();
    }

    private Map<String, Object> transferToMap(Transfer t) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", t.getId());
        map.put("sourceAccountId", t.getSourceAccountId());
        map.put("destinationAccountId", t.getDestinationAccountId());
        map.put("sourceUserId", t.getSourceUserId() != null ? t.getSourceUserId() : "");
        map.put("destinationUserId", t.getDestinationUserId() != null ? t.getDestinationUserId() : "");
        map.put("amount", t.getAmount().toPlainString());
        map.put("sourceCurrency", t.getSourceCurrency());
        map.put("destinationCurrency", t.getDestinationCurrency());
        map.put("status", t.getStatus());
        map.put("idempotencyKey", t.getIdempotencyKey() != null ? t.getIdempotencyKey() : "");
        map.put("description", t.getDescription() != null ? t.getDescription() : "");
        map.put("createdAt", t.getCreatedAt() != null ? Timestamp.ofTimeSecondsAndNanos(t.getCreatedAt().getEpochSecond(), t.getCreatedAt().getNano()) : Timestamp.now());
        if (t.getCompletedAt() != null) {
            map.put("completedAt", Timestamp.ofTimeSecondsAndNanos(t.getCompletedAt().getEpochSecond(), t.getCompletedAt().getNano()));
        }
        return map;
    }

    /**
     * Lee el campo {@code amount} soportando ambos formatos durante la transición de la
     * mejora #16 (double legacy → String decimal exacto). Ver {@code AccountRepository
     * .readBalance} para el mismo patrón y su justificación.
     */
    private BigDecimal readAmount(DocumentSnapshot doc) {
        Object raw = doc.get("amount");
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        if (raw instanceof String s) {
            return new BigDecimal(s);
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

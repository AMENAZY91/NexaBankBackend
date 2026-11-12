package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.nexabank.api.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Repository para la colección {@code transactions} de Firestore.
 *
 * <p>No incluye operación {@code delete} para transacciones COMPLETED.
 * Las transacciones completadas no se eliminan físicamente.
 */
@Repository
public class TransactionRepository {

    private static final Logger log = LoggerFactory.getLogger(TransactionRepository.class);
    private static final String COLLECTION = "transactions";

    private final Firestore firestore;

    public TransactionRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<Transaction> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(documentToTransaction(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding transaction: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public List<Transaction> findByAccountId(String accountId) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("accountId", accountId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToTransaction)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding transactions for account: {}", accountId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public List<Transaction> findByUserId(String userId) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("userId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToTransaction)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding transactions for user: {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    /**
     * Resultado de una consulta paginada por cursor: los elementos de la página
     * y el ID del último documento (para usar como cursor de la siguiente página),
     * o {@code null} si no hay más resultados.
     */
    public record CursorPage(List<Transaction> items, String nextCursor) {
    }

    /**
     * Pagina transacciones de una cuenta usando cursor real de Firestore
     * ({@code startAfter}/{@code limit}), ordenadas por {@code createdAt} descendente.
     *
     * @param accountId cuenta a filtrar
     * @param cursor    ID del último documento visto (null para la primera página)
     * @param limit     tamaño de página
     */
    public CursorPage findByAccountIdAfter(String accountId, String cursor, int limit) {
        return queryAfter(firestore.collection(COLLECTION).whereEqualTo("accountId", accountId), cursor, limit);
    }

    /**
     * Pagina transacciones de un usuario usando cursor real de Firestore
     * ({@code startAfter}/{@code limit}), ordenadas por {@code createdAt} descendente.
     */
    public CursorPage findByUserIdAfter(String userId, String cursor, int limit) {
        return queryAfter(firestore.collection(COLLECTION).whereEqualTo("userId", userId), cursor, limit);
    }

    private CursorPage queryAfter(Query baseQuery, String cursor, int limit) {
        try {
            Query query = baseQuery.orderBy("createdAt", Query.Direction.DESCENDING).limit(limit);
            if (cursor != null && !cursor.isBlank()) {
                DocumentSnapshot cursorDoc = firestore.collection(COLLECTION).document(cursor).get().get();
                if (cursorDoc.exists()) {
                    query = query.startAfter(cursorDoc);
                }
            }
            QuerySnapshot snapshot = query.get().get();
            List<Transaction> items = snapshot.getDocuments().stream()
                    .map(this::documentToTransaction)
                    .toList();
            String next = (items.size() == limit && !snapshot.getDocuments().isEmpty())
                    ? snapshot.getDocuments().get(snapshot.getDocuments().size() - 1).getId()
                    : null;
            return new CursorPage(items, next);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error paging transactions by cursor", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public Transaction save(Transaction transaction) {
        try {
            if (transaction.getId() == null) {
                transaction.setId(generateId());
            }
            firestore.collection(COLLECTION).document(transaction.getId())
                    .set(transactionToMap(transaction)).get();
            log.debug("Saved transaction: {}", transaction.getId());
            return transaction;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving transaction: {}", transaction.getId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    public List<Transaction> findAll() {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION).get().get();
            return query.getDocuments().stream()
                    .map(this::documentToTransaction)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding all transactions", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public long count() {
        try {
            return firestore.collection(COLLECTION).get().get().size();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error counting transactions", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    private String generateId() {
        String id;
        int attempts = 0;
        do {
            id = "TX-" + String.format("%06d", (int) (Math.random() * 999999) + 1);
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

    private Transaction documentToTransaction(DocumentSnapshot doc) {
        return Transaction.builder()
                .id(doc.getString("id"))
                .accountId(doc.getString("accountId"))
                .userId(doc.getString("userId"))
                .type(doc.getString("type"))
                .category(doc.getString("category"))
                .amount(readAmount(doc))
                .currency(doc.getString("currency"))
                .description(doc.getString("description"))
                .status(doc.getString("status"))
                .timestamp(toInstant(doc.getTimestamp("timestamp")))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .build();
    }

    private Map<String, Object> transactionToMap(Transaction tx) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", tx.getId());
        map.put("accountId", tx.getAccountId());
        map.put("userId", tx.getUserId());
        map.put("type", tx.getType());
        map.put("category", tx.getCategory() != null ? tx.getCategory() : "");
        map.put("amount", tx.getAmount().toPlainString());
        map.put("currency", tx.getCurrency());
        map.put("description", tx.getDescription() != null ? tx.getDescription() : "");
        map.put("status", tx.getStatus());
        map.put("timestamp", tx.getTimestamp() != null ? Timestamp.ofTimeSecondsAndNanos(tx.getTimestamp().getEpochSecond(), tx.getTimestamp().getNano()) : Timestamp.now());
        map.put("createdAt", tx.getCreatedAt() != null ? Timestamp.ofTimeSecondsAndNanos(tx.getCreatedAt().getEpochSecond(), tx.getCreatedAt().getNano()) : Timestamp.now());
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

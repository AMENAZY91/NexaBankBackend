package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.nexabank.api.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Repository para la colección {@code notifications} de Firestore.
 */
@Repository
public class NotificationRepository {

    private static final Logger log = LoggerFactory.getLogger(NotificationRepository.class);
    private static final String COLLECTION = "notifications";

    private final Firestore firestore;

    public NotificationRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<Notification> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(documentToNotification(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding notification: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public Notification save(Notification notification) {
        try {
            if (notification.getId() == null) {
                notification.setId(generateId());
            }
            firestore.collection(COLLECTION).document(notification.getId())
                    .set(notificationToMap(notification)).get();
            log.debug("Saved notification: {}", notification.getId());
            return notification;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving notification: {}", notification.getId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    public void markRead(String id) {
        try {
            firestore.collection(COLLECTION).document(id).update(Map.of("read", true)).get();
            log.debug("Marked notification {} as read", id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error marking notification as read: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    /**
     * Resultado de una consulta paginada por cursor: los elementos de la página
     * y el ID del último documento (para usar como cursor de la siguiente página),
     * o {@code null} si no hay más resultados.
     */
    public record CursorPage(List<Notification> items, String nextCursor) {
    }

    /**
     * Pagina notificaciones de un usuario usando cursor real de Firestore
     * ({@code startAfter}/{@code limit}), ordenadas por {@code createdAt} descendente —
     * mismo patrón que {@code TransactionRepository.findByUserIdAfter} (mejora de
     * paginación real, sin traer todo el historial a memoria).
     */
    public CursorPage findByUserIdAfter(String userId, String cursor, int limit) {
        try {
            Query query = firestore.collection(COLLECTION)
                    .whereEqualTo("userId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit);

            if (cursor != null && !cursor.isBlank()) {
                DocumentSnapshot cursorDoc = firestore.collection(COLLECTION).document(cursor).get().get();
                if (cursorDoc.exists()) {
                    query = query.startAfter(cursorDoc);
                }
            }

            QuerySnapshot snapshot = query.get().get();
            List<Notification> items = snapshot.getDocuments().stream()
                    .map(this::documentToNotification)
                    .toList();
            String next = (items.size() == limit && !snapshot.getDocuments().isEmpty())
                    ? snapshot.getDocuments().get(snapshot.getDocuments().size() - 1).getId()
                    : null;
            return new CursorPage(items, next);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error paging notifications by cursor for user: {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    private String generateId() {
        return "NOT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Notification documentToNotification(DocumentSnapshot doc) {
        return Notification.builder()
                .id(doc.getString("id"))
                .userId(doc.getString("userId"))
                .type(doc.getString("type"))
                .title(doc.getString("title"))
                .body(doc.getString("body"))
                .read(Boolean.TRUE.equals(doc.getBoolean("read")))
                .relatedResourceType(doc.getString("relatedResourceType"))
                .relatedResourceId(doc.getString("relatedResourceId"))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .build();
    }

    private Map<String, Object> notificationToMap(Notification notification) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", notification.getId());
        map.put("userId", notification.getUserId());
        map.put("type", notification.getType());
        map.put("title", notification.getTitle());
        map.put("body", notification.getBody() != null ? notification.getBody() : "");
        map.put("read", notification.isRead());
        map.put("relatedResourceType", notification.getRelatedResourceType());
        map.put("relatedResourceId", notification.getRelatedResourceId());
        map.put("createdAt", notification.getCreatedAt() != null
                ? Timestamp.ofTimeSecondsAndNanos(notification.getCreatedAt().getEpochSecond(), notification.getCreatedAt().getNano())
                : Timestamp.now());
        return map;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

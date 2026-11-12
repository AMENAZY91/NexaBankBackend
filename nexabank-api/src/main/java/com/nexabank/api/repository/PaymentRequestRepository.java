package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.nexabank.api.model.PaymentRequest;
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
 * Repository para la colección {@code payment_requests} de Firestore.
 */
@Repository
public class PaymentRequestRepository {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestRepository.class);
    private static final String COLLECTION = "payment_requests";

    private final Firestore firestore;

    public PaymentRequestRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<PaymentRequest> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(documentToPaymentRequest(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding payment request: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public List<PaymentRequest> findByRequesterId(String requesterId) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("requesterId", requesterId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get().get();
            return query.getDocuments().stream().map(this::documentToPaymentRequest).toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding payment requests for requester: {}", requesterId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public List<PaymentRequest> findByPayerUserId(String payerUserId) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("payerUserId", payerUserId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get().get();
            return query.getDocuments().stream().map(this::documentToPaymentRequest).toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding payment requests for payer: {}", payerUserId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    /**
     * Solicitudes {@code PENDING} cuya expiración ya pasó — usado por el job de barrido
     * periódico ({@code PaymentRequestService#expireOverduePendingRequests}).
     *
     * <p>Filtra {@code expiresAt} en memoria (no vía {@code whereLessThan} de Firestore)
     * deliberadamente: combinar una igualdad ({@code status}) con un rango
     * ({@code expiresAt}) en la propia query requiere un índice compuesto en Firestore
     * que no existe por defecto (falla con {@code FAILED_PRECONDITION} la primera vez
     * que corre). La cola de solicitudes {@code PENDING} es, por naturaleza, pequeña y
     * acotada (nunca acumula el historial completo), así que traerla completa y filtrar
     * en Java es aceptable aquí — a diferencia de los listados paginados (transacciones,
     * notificaciones, etc.), donde sí hace falta el índice real (ver docs/FIRESTORE_INDEXES.md).
     */
    public List<PaymentRequest> findPendingExpiredBefore(Instant now) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("status", "PENDING")
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToPaymentRequest)
                    .filter(pr -> pr.getExpiresAt() != null && pr.getExpiresAt().isBefore(now))
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding expired pending payment requests", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public PaymentRequest save(PaymentRequest paymentRequest) {
        try {
            if (paymentRequest.getId() == null) {
                paymentRequest.setId(generateId());
            }
            firestore.collection(COLLECTION).document(paymentRequest.getId())
                    .set(paymentRequestToMap(paymentRequest)).get();
            log.debug("Saved payment request: {}", paymentRequest.getId());
            return paymentRequest;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving payment request: {}", paymentRequest.getId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    private String generateId() {
        return "PRQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private PaymentRequest documentToPaymentRequest(DocumentSnapshot doc) {
        return PaymentRequest.builder()
                .id(doc.getString("id"))
                .requesterId(doc.getString("requesterId"))
                .requesterAccountId(doc.getString("requesterAccountId"))
                .payerUserId(doc.getString("payerUserId"))
                .payerPhone(doc.getString("payerPhone"))
                .amount(readAmount(doc))
                .currency(doc.getString("currency"))
                .description(doc.getString("description"))
                .status(doc.getString("status"))
                .relatedTransferId(doc.getString("relatedTransferId"))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .expiresAt(toInstant(doc.getTimestamp("expiresAt")))
                .build();
    }

    private Map<String, Object> paymentRequestToMap(PaymentRequest pr) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", pr.getId());
        map.put("requesterId", pr.getRequesterId());
        map.put("requesterAccountId", pr.getRequesterAccountId());
        map.put("payerUserId", pr.getPayerUserId());
        map.put("payerPhone", pr.getPayerPhone());
        map.put("amount", pr.getAmount().toPlainString());
        map.put("currency", pr.getCurrency());
        map.put("description", pr.getDescription() != null ? pr.getDescription() : "");
        map.put("status", pr.getStatus());
        map.put("relatedTransferId", pr.getRelatedTransferId() != null ? pr.getRelatedTransferId() : "");
        map.put("createdAt", pr.getCreatedAt() != null
                ? Timestamp.ofTimeSecondsAndNanos(pr.getCreatedAt().getEpochSecond(), pr.getCreatedAt().getNano())
                : Timestamp.now());
        if (pr.getExpiresAt() != null) {
            map.put("expiresAt", Timestamp.ofTimeSecondsAndNanos(pr.getExpiresAt().getEpochSecond(), pr.getExpiresAt().getNano()));
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

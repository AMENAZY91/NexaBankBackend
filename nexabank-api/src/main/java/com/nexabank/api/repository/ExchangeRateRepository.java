package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.nexabank.api.model.ExchangeRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Repository para la colección {@code exchange_rates} de Firestore.
 *
 * <p>Almacena cotizaciones de divisas para transferencias internacionales.
 */
@Repository
public class ExchangeRateRepository {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateRepository.class);
    private static final String COLLECTION = "exchange_rates";

    private final Firestore firestore;

    public ExchangeRateRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<ExchangeRate> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(documentToExchangeRate(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding exchange rate: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public Optional<ExchangeRate> findLatestByPair(String baseCurrency, String targetCurrency) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("baseCurrency", baseCurrency.toUpperCase())
                    .whereEqualTo("targetCurrency", targetCurrency.toUpperCase())
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get().get();
            if (!query.isEmpty()) {
                return Optional.of(documentToExchangeRate(query.getDocuments().get(0)));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding exchange rate for pair {}/{}", baseCurrency, targetCurrency, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public ExchangeRate save(ExchangeRate rate) {
        try {
            if (rate.getId() == null) {
                rate.setId("FX-" + String.format("%06d", (int) (Math.random() * 999999) + 1));
            }
            if (rate.getTimestamp() == null) {
                rate.setTimestamp(Instant.now());
            }
            firestore.collection(COLLECTION).document(rate.getId())
                    .set(exchangeRateToMap(rate)).get();
            log.debug("Saved exchange rate: {} {}→{} {}", rate.getId(),
                    rate.getBaseCurrency(), rate.getTargetCurrency(), rate.getRate());
            return rate;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving exchange rate: {}", rate.getId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    public List<ExchangeRate> findAll() {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToExchangeRate)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding all exchange rates", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    private ExchangeRate documentToExchangeRate(DocumentSnapshot doc) {
        return ExchangeRate.builder()
                .id(doc.getString("id"))
                .baseCurrency(doc.getString("baseCurrency"))
                .targetCurrency(doc.getString("targetCurrency"))
                .rate(doc.getDouble("rate") != null ? BigDecimal.valueOf(doc.getDouble("rate")) : BigDecimal.ZERO)
                .timestamp(toInstant(doc.getTimestamp("timestamp")))
                .source(doc.getString("source"))
                .expiresAt(toInstant(doc.getTimestamp("expiresAt")))
                .build();
    }

    private Map<String, Object> exchangeRateToMap(ExchangeRate rate) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", rate.getId());
        map.put("baseCurrency", rate.getBaseCurrency());
        map.put("targetCurrency", rate.getTargetCurrency());
        map.put("rate", rate.getRate().doubleValue());
        map.put("timestamp", rate.getTimestamp() != null
                ? Timestamp.ofTimeSecondsAndNanos(rate.getTimestamp().getEpochSecond(), rate.getTimestamp().getNano())
                : Timestamp.now());
        map.put("source", rate.getSource());
        if (rate.getExpiresAt() != null) {
            map.put("expiresAt", Timestamp.ofTimeSecondsAndNanos(rate.getExpiresAt().getEpochSecond(),
                    rate.getExpiresAt().getNano()));
        }
        return map;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

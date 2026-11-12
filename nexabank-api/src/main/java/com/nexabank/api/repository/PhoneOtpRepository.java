package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.nexabank.api.model.PhoneOtp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Repository para la colección {@code phone_otps} de Firestore. Documento indexado por
 * número de teléfono: cada {@code save} sobrescribe el OTP vigente anterior.
 */
@Repository
public class PhoneOtpRepository {

    private static final Logger log = LoggerFactory.getLogger(PhoneOtpRepository.class);
    private static final String COLLECTION = "phone_otps";

    private final Firestore firestore;

    public PhoneOtpRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<PhoneOtp> findByPhone(String phone) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(phone).get().get();
            if (doc.exists()) {
                return Optional.of(documentToOtp(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding phone OTP for: {}", phone, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public PhoneOtp save(PhoneOtp otp) {
        try {
            firestore.collection(COLLECTION).document(otp.getPhone())
                    .set(otpToMap(otp)).get();
            return otp;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving phone OTP for: {}", otp.getPhone(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    public void registerFailedAttempt(String phone, int attempts) {
        try {
            firestore.collection(COLLECTION).document(phone).update(Map.of("attempts", attempts)).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error updating phone OTP attempts for: {}", phone, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    public void markUsed(String phone) {
        try {
            firestore.collection(COLLECTION).document(phone).update(Map.of("used", true)).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error marking phone OTP as used: {}", phone, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    private PhoneOtp documentToOtp(DocumentSnapshot doc) {
        Long attempts = doc.getLong("attempts");
        return PhoneOtp.builder()
                .phone(doc.getString("phone"))
                .codeHash(doc.getString("codeHash"))
                .expiresAt(toInstant(doc.getTimestamp("expiresAt")))
                .attempts(attempts != null ? attempts.intValue() : 0)
                .used(Boolean.TRUE.equals(doc.getBoolean("used")))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .build();
    }

    private Map<String, Object> otpToMap(PhoneOtp otp) {
        Map<String, Object> map = new HashMap<>();
        map.put("phone", otp.getPhone());
        map.put("codeHash", otp.getCodeHash());
        map.put("expiresAt", Timestamp.ofTimeSecondsAndNanos(otp.getExpiresAt().getEpochSecond(), otp.getExpiresAt().getNano()));
        map.put("attempts", otp.getAttempts());
        map.put("used", otp.isUsed());
        map.put("createdAt", otp.getCreatedAt() != null
                ? Timestamp.ofTimeSecondsAndNanos(otp.getCreatedAt().getEpochSecond(), otp.getCreatedAt().getNano())
                : Timestamp.now());
        return map;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

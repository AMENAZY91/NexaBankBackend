package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.nexabank.api.model.KycProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Repository para la colección {@code kyc_profiles} de Firestore. El ID del documento
 * es {@code userId} (ver {@link KycProfile}), así que no hay generación de ID propia.
 */
@Repository
public class KycProfileRepository {

    private static final Logger log = LoggerFactory.getLogger(KycProfileRepository.class);
    private static final String COLLECTION = "kyc_profiles";

    private final Firestore firestore;

    public KycProfileRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<KycProfile> findByUserId(String userId) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(userId).get().get();
            if (doc.exists()) {
                return Optional.of(documentToKycProfile(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding KYC profile for user: {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    /**
     * Solicitudes {@code SUBMITTED} o {@code UNDER_REVIEW} — cola de revisión para el admin.
     */
    public List<KycProfile> findPending() {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereIn("status", List.of("SUBMITTED", "UNDER_REVIEW"))
                    .get().get();
            return query.getDocuments().stream().map(this::documentToKycProfile).toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding pending KYC profiles", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public KycProfile save(KycProfile profile) {
        try {
            firestore.collection(COLLECTION).document(profile.getUserId())
                    .set(kycProfileToMap(profile)).get();
            log.debug("Saved KYC profile for user: {}", profile.getUserId());
            return profile;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving KYC profile for user: {}", profile.getUserId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    private KycProfile documentToKycProfile(DocumentSnapshot doc) {
        return KycProfile.builder()
                .userId(doc.getString("userId"))
                .status(doc.getString("status"))
                .verificationId(doc.getString("verificationId"))
                .documentType(doc.getString("documentType"))
                .documentNumber(doc.getString("documentNumber"))
                .fullName(doc.getString("fullName"))
                .dateOfBirth(doc.getString("dateOfBirth"))
                .address(doc.getString("address"))
                .submittedAt(toInstant(doc.getTimestamp("submittedAt")))
                .reviewedAt(toInstant(doc.getTimestamp("reviewedAt")))
                .reviewerId(doc.getString("reviewerId"))
                .rejectionReason(doc.getString("rejectionReason"))
                .build();
    }

    private Map<String, Object> kycProfileToMap(KycProfile profile) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", profile.getUserId());
        map.put("status", profile.getStatus());
        map.put("verificationId", profile.getVerificationId() != null ? profile.getVerificationId() : "");
        map.put("documentType", profile.getDocumentType() != null ? profile.getDocumentType() : "");
        map.put("documentNumber", profile.getDocumentNumber() != null ? profile.getDocumentNumber() : "");
        map.put("fullName", profile.getFullName() != null ? profile.getFullName() : "");
        map.put("dateOfBirth", profile.getDateOfBirth() != null ? profile.getDateOfBirth() : "");
        map.put("address", profile.getAddress() != null ? profile.getAddress() : "");
        map.put("reviewerId", profile.getReviewerId() != null ? profile.getReviewerId() : "");
        map.put("rejectionReason", profile.getRejectionReason() != null ? profile.getRejectionReason() : "");
        if (profile.getSubmittedAt() != null) {
            map.put("submittedAt", Timestamp.ofTimeSecondsAndNanos(profile.getSubmittedAt().getEpochSecond(), profile.getSubmittedAt().getNano()));
        }
        if (profile.getReviewedAt() != null) {
            map.put("reviewedAt", Timestamp.ofTimeSecondsAndNanos(profile.getReviewedAt().getEpochSecond(), profile.getReviewedAt().getNano()));
        }
        return map;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

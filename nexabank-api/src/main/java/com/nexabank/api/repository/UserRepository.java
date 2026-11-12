package com.nexabank.api.repository;

import com.google.cloud.firestore.*;
import com.nexabank.api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Repository para la colección {@code users} de Firestore.
 */
@Repository
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);
    private static final String COLLECTION = "users";

    private final Firestore firestore;

    public UserRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<User> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(documentToUser(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding user by id: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public Optional<User> findByEmail(String email) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get().get();

            if (!query.isEmpty()) {
                return Optional.of(documentToUser(query.getDocuments().get(0)));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding user by email: {}", email, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public Optional<User> findByPhone(String phone) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("phone", phone)
                    .limit(1)
                    .get().get();

            if (!query.isEmpty()) {
                return Optional.of(documentToUser(query.getDocuments().get(0)));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding user by phone: {}", phone, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public boolean existsByPhone(String phone) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("phone", phone)
                    .limit(1)
                    .get().get();
            return !query.isEmpty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error checking if user exists by phone: {}", phone, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public User save(User user) {
        try {
            if (user.getId() == null) {
                user.setId(generateId());
            }
            firestore.collection(COLLECTION).document(user.getId())
                    .set(userToMap(user)).get();
            log.debug("Saved user: {}", user.getId());
            return user;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving user: {}", user.getId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    public User update(User user) {
        user.setUpdatedAt(Instant.now());
        return save(user);
    }

    public List<User> findAll() {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION).get().get();
            return query.getDocuments().stream()
                    .map(this::documentToUser)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding all users", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public long count() {
        try {
            return firestore.collection(COLLECTION).get().get().size();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error counting users", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    private String generateId() {
        return "USR-" + String.format("%06d",
                (int) (Math.random() * 999999) + 1);
    }

    @SuppressWarnings("unchecked")
    private User documentToUser(DocumentSnapshot doc) {
        return User.builder()
                .id(doc.getString("id"))
                .firstName(doc.getString("firstName"))
                .lastName(doc.getString("lastName"))
                .documentId(doc.getString("documentId"))
                .address(doc.getString("address"))
                .country(doc.getString("country"))
                .email(doc.getString("email"))
                .displayName(doc.getString("displayName"))
                .phone(doc.getString("phone"))
                .status(doc.getString("status"))
                .roles(doc.get("roles") != null ? (java.util.List<String>) doc.get("roles") : java.util.List.of("USER"))
                .pin(doc.getString("pin"))
                .biometricEnabled(doc.getBoolean("biometricEnabled"))
                .faceTemplate(doc.getString("faceTemplate"))
                .faceVerified(doc.getBoolean("faceVerified"))
                .pinFailedAttempts(doc.getLong("pinFailedAttempts") != null ? doc.getLong("pinFailedAttempts").intValue() : 0)
                .pinLockedUntil(toInstant(doc.getTimestamp("pinLockedUntil")))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .updatedAt(toInstant(doc.getTimestamp("updatedAt")))
                .lastLoginAt(toInstant(doc.getTimestamp("lastLoginAt")))
                .build();
    }

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("firstName", user.getFirstName() != null ? user.getFirstName() : "");
        map.put("lastName", user.getLastName() != null ? user.getLastName() : "");
        map.put("documentId", user.getDocumentId() != null ? user.getDocumentId() : "");
        map.put("address", user.getAddress() != null ? user.getAddress() : "");
        map.put("country", user.getCountry() != null ? user.getCountry() : "");
        map.put("email", user.getEmail() != null ? user.getEmail() : "");
        map.put("displayName", user.getDisplayName() != null ? user.getDisplayName() : "");
        map.put("phone", user.getPhone() != null ? user.getPhone() : "");
        map.put("status", user.getStatus() != null ? user.getStatus() : "ACTIVE");
        map.put("roles", user.getRoles() != null ? user.getRoles() : java.util.List.of("USER"));
        map.put("pin", user.getPin());
        map.put("biometricEnabled", user.getBiometricEnabled() != null ? user.getBiometricEnabled() : false);
        map.put("faceTemplate", user.getFaceTemplate());
        map.put("faceVerified", user.getFaceVerified() != null ? user.getFaceVerified() : false);
        map.put("pinFailedAttempts", user.getPinFailedAttempts());
        if (user.getPinLockedUntil() != null) {
            map.put("pinLockedUntil", com.google.cloud.Timestamp.ofTimeSecondsAndNanos(user.getPinLockedUntil().getEpochSecond(), user.getPinLockedUntil().getNano()));
        }
        map.put("createdAt", user.getCreatedAt() != null ? com.google.cloud.Timestamp.ofTimeSecondsAndNanos(user.getCreatedAt().getEpochSecond(), user.getCreatedAt().getNano()) : com.google.cloud.Timestamp.now());
        map.put("updatedAt", com.google.cloud.Timestamp.now());
        map.put("lastLoginAt", user.getLastLoginAt() != null ? com.google.cloud.Timestamp.ofTimeSecondsAndNanos(user.getLastLoginAt().getEpochSecond(), user.getLastLoginAt().getNano()) : com.google.cloud.Timestamp.now());
        return map;
    }

    private Instant toInstant(com.google.cloud.Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

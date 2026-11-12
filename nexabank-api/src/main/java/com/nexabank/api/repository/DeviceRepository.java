package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.nexabank.api.model.Device;
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
 * Repository para la colección {@code devices} de Firestore.
 */
@Repository
public class DeviceRepository {

    private static final Logger log = LoggerFactory.getLogger(DeviceRepository.class);
    private static final String COLLECTION = "devices";

    private final Firestore firestore;

    public DeviceRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<Device> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(documentToDevice(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding device: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    /**
     * Busca el documento de dispositivo de un usuario por el identificador opaco que
     * genera el cliente — no es el ID del documento (ver {@link Device}).
     */
    public Optional<Device> findByUserIdAndDeviceId(String userId, String deviceId) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("deviceId", deviceId)
                    .limit(1)
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToDevice)
                    .findFirst();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding device for user {} with deviceId {}", userId, deviceId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public List<Device> findByUserId(String userId) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("userId", userId)
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToDevice)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding devices for user: {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public Device save(Device device) {
        try {
            if (device.getId() == null) {
                device.setId(generateId());
            }
            firestore.collection(COLLECTION).document(device.getId())
                    .set(deviceToMap(device)).get();
            log.debug("Saved device: {}", device.getId());
            return device;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving device: {}", device.getId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    public void revokeTrust(String id) {
        try {
            firestore.collection(COLLECTION).document(id).update(Map.of("trusted", false)).get();
            log.debug("Revoked trust for device {}", id);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error revoking trust for device: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    private String generateId() {
        return "DEV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Device documentToDevice(DocumentSnapshot doc) {
        return Device.builder()
                .id(doc.getString("id"))
                .userId(doc.getString("userId"))
                .deviceId(doc.getString("deviceId"))
                .deviceName(doc.getString("deviceName"))
                .platform(doc.getString("platform"))
                .trusted(Boolean.TRUE.equals(doc.getBoolean("trusted")))
                .lastSeenAt(toInstant(doc.getTimestamp("lastSeenAt")))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .build();
    }

    private Map<String, Object> deviceToMap(Device device) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", device.getId());
        map.put("userId", device.getUserId());
        map.put("deviceId", device.getDeviceId());
        map.put("deviceName", device.getDeviceName() != null ? device.getDeviceName() : "");
        map.put("platform", device.getPlatform() != null ? device.getPlatform() : "");
        map.put("trusted", device.isTrusted());
        map.put("lastSeenAt", device.getLastSeenAt() != null
                ? Timestamp.ofTimeSecondsAndNanos(device.getLastSeenAt().getEpochSecond(), device.getLastSeenAt().getNano())
                : Timestamp.now());
        map.put("createdAt", device.getCreatedAt() != null
                ? Timestamp.ofTimeSecondsAndNanos(device.getCreatedAt().getEpochSecond(), device.getCreatedAt().getNano())
                : Timestamp.now());
        return map;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

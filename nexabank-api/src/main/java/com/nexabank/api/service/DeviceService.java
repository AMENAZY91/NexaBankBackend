package com.nexabank.api.service;

import com.nexabank.api.dto.device.DeviceResponse;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.DeviceMapper;
import com.nexabank.api.model.Device;
import com.nexabank.api.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Servicio de gestión de dispositivos.
 *
 * <p>{@link #recordSeen} es de uso interno — llamado desde {@code AuthService} tras un
 * PIN configurado/verificado correctamente (ver docs/IMPLEMENTATION_PLAN.md, Fase 3).
 * No existe ningún endpoint público para registrar un dispositivo directamente.
 */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;
    private final DeviceMapper deviceMapper;

    public DeviceService(DeviceRepository deviceRepository, DeviceMapper deviceMapper) {
        this.deviceRepository = deviceRepository;
        this.deviceMapper = deviceMapper;
    }

    /**
     * Upsert por {@code (userId, deviceId)}: crea el dispositivo si es la primera vez que
     * se ve, o actualiza {@code lastSeenAt} (y re-confirma {@code trusted=true}) si ya existía.
     * No-op silencioso si {@code deviceId} es nulo o vacío — permite que clientes que todavía
     * no implementan el identificador de dispositivo sigan funcionando sin cambios.
     */
    public void recordSeen(String userId, String deviceId, String deviceName, String platform) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }

        Instant now = Instant.now();
        Device device = deviceRepository.findByUserIdAndDeviceId(userId, deviceId)
                .map(existing -> existing.toBuilder()
                        .deviceName(deviceName != null ? deviceName : existing.getDeviceName())
                        .platform(platform != null ? platform : existing.getPlatform())
                        .trusted(true)
                        .lastSeenAt(now)
                        .build())
                .orElseGet(() -> Device.builder()
                        .userId(userId)
                        .deviceId(deviceId)
                        .deviceName(deviceName)
                        .platform(platform)
                        .trusted(true)
                        .lastSeenAt(now)
                        .createdAt(now)
                        .build());

        Device saved = deviceRepository.save(device);
        log.info("Device {} recorded for user {}", saved.getId(), userId);
    }

    public List<DeviceResponse> getDevicesByUser(String userId) {
        return deviceRepository.findByUserId(userId).stream()
                .map(deviceMapper::toResponse)
                .toList();
    }

    public void revokeDevice(String id, String userId) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with ID: " + id));

        if (userId != null && !userId.equals(device.getUserId())) {
            throw new UnauthorizedOperationException("You do not have access to this device");
        }

        deviceRepository.revokeTrust(id);
    }
}

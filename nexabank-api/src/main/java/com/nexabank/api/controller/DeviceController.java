package com.nexabank.api.controller;

import com.nexabank.api.dto.device.DeviceResponse;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestión de dispositivos reconocidos del usuario autenticado.
 *
 * <p>No expone creación directa — los dispositivos se registran como efecto
 * secundario de {@code POST /api/v1/auth/pin/setup}/{@code pin/verify}
 * (ver {@code AuthService}, docs/IMPLEMENTATION_PLAN.md Fase 3).
 */
@RestController
@RequestMapping("/api/v1/me/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final SecurityUtils securityUtils;

    public DeviceController(DeviceService deviceService, SecurityUtils securityUtils) {
        this.deviceService = deviceService;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public ResponseEntity<List<DeviceResponse>> getDevices() {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(deviceService.getDevicesByUser(userId));
    }

    /**
     * Revoca la confianza del dispositivo ({@code trusted=false}) — no es un borrado
     * físico del historial del dispositivo.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeDevice(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        deviceService.revokeDevice(id, userId);
        return ResponseEntity.noContent().build();
    }
}

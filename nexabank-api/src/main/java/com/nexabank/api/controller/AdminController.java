package com.nexabank.api.controller;

import com.nexabank.api.dto.user.UserResponse;
import com.nexabank.api.service.AdminService;
import com.nexabank.api.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador administrativo.
 *
 * <p>Todas las rutas requieren rol ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final SecurityUtils securityUtils;

    public AdminController(AdminService adminService, SecurityUtils securityUtils) {
        this.adminService = adminService;
        this.securityUtils = securityUtils;
    }

    /**
     * Obtiene métricas globales del sistema.
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(adminService.getMetrics());
    }

    /**
     * Lista todos los usuarios registrados.
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getUsers() {
        return ResponseEntity.ok(adminService.getUsers());
    }

    /**
     * Lista eventos de auditoría recientes.
     */
    @GetMapping("/audit-events")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAuditEvents() {
        return ResponseEntity.ok(adminService.getAuditEvents());
    }
}
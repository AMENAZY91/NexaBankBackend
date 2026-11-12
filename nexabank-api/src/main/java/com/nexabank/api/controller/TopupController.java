package com.nexabank.api.controller;

import com.nexabank.api.dto.topup.CreateTopupRequest;
import com.nexabank.api.dto.topup.TopupResponse;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.ConfirmationTokenService;
import com.nexabank.api.service.TopupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de recargas (topups).
 *
 * <p>Rutas:
 * <ul>
 *   <li>GET /api/v1/topups — listar recargas del usuario</li>
 *   <li>GET /api/v1/topups/{id} — obtener recarga</li>
 *   <li>POST /api/v1/topups — ejecutar recarga (requiere Idempotency-Key, con X-Confirmation-Token)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/topups")
public class TopupController {

    private final TopupService topupService;
    private final SecurityUtils securityUtils;
    private final ConfirmationTokenService confirmationTokenService;

    public TopupController(TopupService topupService, SecurityUtils securityUtils,
                            ConfirmationTokenService confirmationTokenService) {
        this.topupService = topupService;
        this.securityUtils = securityUtils;
        this.confirmationTokenService = confirmationTokenService;
    }

    @GetMapping
    public ResponseEntity<List<TopupResponse>> getTopups() {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(topupService.getTopupsByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TopupResponse> getTopup(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(topupService.getTopupById(id, userId));
    }

    @PostMapping
    public ResponseEntity<TopupResponse> createTopup(
            @Valid @RequestBody CreateTopupRequest request,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Confirmation-Token", required = false) String confirmationToken) {
        String userId = securityUtils.getCurrentUserId();
        confirmationTokenService.validateAndConsume(confirmationToken, userId);
        TopupResponse created = topupService.createTopup(request, idempotencyKey, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}

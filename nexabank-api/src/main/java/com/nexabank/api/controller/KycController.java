package com.nexabank.api.controller;

import com.nexabank.api.dto.kyc.KycProfileResponse;
import com.nexabank.api.dto.kyc.SubmitKycRequest;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.KycService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador de KYC del usuario autenticado.
 *
 * <p>Rutas:
 * <ul>
 *   <li>GET /api/v1/me/kyc — estado actual (NOT_STARTED si nunca se envió nada)</li>
 *   <li>POST /api/v1/me/kyc — enviar/reenviar verificación</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/me/kyc")
public class KycController {

    private final KycService kycService;
    private final SecurityUtils securityUtils;

    public KycController(KycService kycService, SecurityUtils securityUtils) {
        this.kycService = kycService;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public ResponseEntity<KycProfileResponse> getMyKyc() {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(kycService.getMyProfile(userId));
    }

    @PostMapping
    public ResponseEntity<KycProfileResponse> submitKyc(@Valid @RequestBody SubmitKycRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(kycService.submit(request, userId));
    }
}

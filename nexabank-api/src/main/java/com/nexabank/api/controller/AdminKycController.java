package com.nexabank.api.controller;

import com.nexabank.api.dto.kyc.KycProfileResponse;
import com.nexabank.api.dto.kyc.RejectKycRequest;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.KycService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Revisión administrativa de KYC. Todas las rutas requieren rol ADMIN
 * (mismo patrón que {@link AdminController}).
 */
@RestController
@RequestMapping("/api/v1/admin/kyc")
public class AdminKycController {

    private final KycService kycService;
    private final SecurityUtils securityUtils;

    public AdminKycController(KycService kycService, SecurityUtils securityUtils) {
        this.kycService = kycService;
        this.securityUtils = securityUtils;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<KycProfileResponse>> getPending() {
        return ResponseEntity.ok(kycService.getPending());
    }

    @PostMapping("/{userId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KycProfileResponse> approve(@PathVariable("userId") String userId) {
        String adminId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(kycService.approve(userId, adminId));
    }

    @PostMapping("/{userId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KycProfileResponse> reject(@PathVariable("userId") String userId,
                                                      @Valid @RequestBody RejectKycRequest request) {
        String adminId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(kycService.reject(userId, adminId, request.getRejectionReason()));
    }
}

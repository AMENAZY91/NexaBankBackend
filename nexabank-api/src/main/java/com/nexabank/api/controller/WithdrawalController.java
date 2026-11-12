package com.nexabank.api.controller;

import com.nexabank.api.dto.withdrawal.CreateWithdrawalRequest;
import com.nexabank.api.dto.withdrawal.WithdrawalResponse;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.ConfirmationTokenService;
import com.nexabank.api.service.WithdrawalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de retiros (withdrawals).
 *
 * <p>Rutas:
 * <ul>
 *   <li>GET /api/v1/withdrawals — listar retiros del usuario</li>
 *   <li>GET /api/v1/withdrawals/{id} — obtener retiro</li>
 *   <li>POST /api/v1/withdrawals — ejecutar retiro (requiere Idempotency-Key, con X-Confirmation-Token)</li>
 *   <li>POST /api/v1/withdrawals/{id}/cancel — cancelar retiro pendiente</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/withdrawals")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;
    private final SecurityUtils securityUtils;
    private final ConfirmationTokenService confirmationTokenService;

    public WithdrawalController(WithdrawalService withdrawalService, SecurityUtils securityUtils,
                                 ConfirmationTokenService confirmationTokenService) {
        this.withdrawalService = withdrawalService;
        this.securityUtils = securityUtils;
        this.confirmationTokenService = confirmationTokenService;
    }

    @GetMapping
    public ResponseEntity<List<WithdrawalResponse>> getWithdrawals() {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(withdrawalService.getWithdrawalsByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WithdrawalResponse> getWithdrawal(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(withdrawalService.getWithdrawalById(id, userId));
    }

    @PostMapping
    public ResponseEntity<WithdrawalResponse> createWithdrawal(
            @Valid @RequestBody CreateWithdrawalRequest request,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Confirmation-Token", required = false) String confirmationToken) {
        String userId = securityUtils.getCurrentUserId();
        confirmationTokenService.validateAndConsume(confirmationToken, userId);
        WithdrawalResponse created = withdrawalService.createWithdrawal(request, idempotencyKey, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<WithdrawalResponse> cancelWithdrawal(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(withdrawalService.cancelWithdrawal(id, userId));
    }
}

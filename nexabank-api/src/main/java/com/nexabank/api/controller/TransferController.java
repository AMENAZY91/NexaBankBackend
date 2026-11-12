package com.nexabank.api.controller;

import com.nexabank.api.dto.transfer.CreateTransferRequest;
import com.nexabank.api.dto.transfer.TransferResponse;
import com.nexabank.api.service.ConfirmationTokenService;
import com.nexabank.api.service.TransferService;
import com.nexabank.api.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Controlador de transferencias con soporte internacional.
 *
 * <p>Rutas:
 * <ul>
 *   <li>GET /api/v1/transfers — listar transferencias del usuario</li>
 *   <li>GET /api/v1/transfers/{id} — obtener transferencia</li>
 *   <li>POST /api/v1/transfers — ejecutar transferencia (requiere Idempotency-Key)</li>
 *   <li>POST /api/v1/transfers/quote — cotización previa</li>
 *   <li>POST /api/v1/transfers/{id}/cancel — cancelar transferencia pendiente</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;
    private final SecurityUtils securityUtils;
    private final ConfirmationTokenService confirmationTokenService;

    public TransferController(TransferService transferService, SecurityUtils securityUtils,
                               ConfirmationTokenService confirmationTokenService) {
        this.transferService = transferService;
        this.securityUtils = securityUtils;
        this.confirmationTokenService = confirmationTokenService;
    }

    @GetMapping
    public ResponseEntity<List<TransferResponse>> getTransfers() {
        String userId = securityUtils.getCurrentUserId();
        List<TransferResponse> transfers = transferService.getTransfersByUser(userId);
        return ResponseEntity.ok(transfers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        TransferResponse transfer = transferService.getTransferById(id, userId);
        return ResponseEntity.ok(transfer);
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Confirmation-Token", required = false) String confirmationToken) {
        String userId = securityUtils.getCurrentUserId();
        confirmationTokenService.validateAndConsume(confirmationToken, userId);
        TransferResponse created = transferService.executeTransfer(request, idempotencyKey, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/quote")
    public ResponseEntity<com.nexabank.api.dto.fx.QuoteResponse> quoteTransfer(
            @Valid @RequestBody CreateTransferRequest request) {
        String userId = securityUtils.getCurrentUserId();
        com.nexabank.api.dto.fx.QuoteResponse quote =
                transferService.quoteTransfer(userId, request);
        return ResponseEntity.ok(quote);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<TransferResponse> cancelTransfer(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        TransferResponse cancelled = transferService.cancelTransfer(id, userId);
        return ResponseEntity.ok(cancelled);
    }
}

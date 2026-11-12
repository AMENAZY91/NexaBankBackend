package com.nexabank.api.controller;

import com.nexabank.api.dto.paymentrequest.CreatePaymentRequestRequest;
import com.nexabank.api.dto.paymentrequest.PayPaymentRequestRequest;
import com.nexabank.api.dto.paymentrequest.PaymentRequestResponse;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.ConfirmationTokenService;
import com.nexabank.api.service.PaymentRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de solicitudes de pago.
 *
 * <p>Rutas:
 * <ul>
 *   <li>POST /api/v1/payment-requests — crear solicitud</li>
 *   <li>GET /api/v1/payment-requests?role=sent|received — listar (default: received)</li>
 *   <li>GET /api/v1/payment-requests/{id} — obtener solicitud</li>
 *   <li>POST /api/v1/payment-requests/{id}/pay — pagar (requiere Idempotency-Key, con X-Confirmation-Token)</li>
 *   <li>POST /api/v1/payment-requests/{id}/decline — rechazar</li>
 *   <li>POST /api/v1/payment-requests/{id}/cancel — cancelar (solo el solicitante)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/payment-requests")
public class PaymentRequestController {

    private final PaymentRequestService paymentRequestService;
    private final SecurityUtils securityUtils;
    private final ConfirmationTokenService confirmationTokenService;

    public PaymentRequestController(PaymentRequestService paymentRequestService, SecurityUtils securityUtils,
                                     ConfirmationTokenService confirmationTokenService) {
        this.paymentRequestService = paymentRequestService;
        this.securityUtils = securityUtils;
        this.confirmationTokenService = confirmationTokenService;
    }

    @PostMapping
    public ResponseEntity<PaymentRequestResponse> createPaymentRequest(
            @Valid @RequestBody CreatePaymentRequestRequest request) {
        String userId = securityUtils.getCurrentUserId();
        PaymentRequestResponse created = paymentRequestService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<PaymentRequestResponse>> getPaymentRequests(
            @RequestParam(value = "role", required = false, defaultValue = "received") String role) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(paymentRequestService.getByUser(userId, role));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentRequestResponse> getPaymentRequest(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(paymentRequestService.getById(id, userId));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<PaymentRequestResponse> pay(
            @PathVariable("id") String id,
            @Valid @RequestBody PayPaymentRequestRequest request,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Confirmation-Token", required = false) String confirmationToken) {
        String userId = securityUtils.getCurrentUserId();
        confirmationTokenService.validateAndConsume(confirmationToken, userId);
        return ResponseEntity.ok(paymentRequestService.pay(id, request, idempotencyKey, userId));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<PaymentRequestResponse> decline(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(paymentRequestService.decline(id, userId));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentRequestResponse> cancel(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(paymentRequestService.cancel(id, userId));
    }
}

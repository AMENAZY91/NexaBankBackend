package com.nexabank.api.controller;

import com.nexabank.api.dto.qr.QrPayRequest;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.ConfirmationTokenService;
import com.nexabank.api.service.QrService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador de pagos por QR.
 *
 * <p>Rutas:
 * <ul>
 *   <li>POST /api/v1/qr/pay</li>
 *   <li>GET /api/v1/qr/decode?data=...</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/qr")
public class QrController {

    private final QrService qrService;
    private final SecurityUtils securityUtils;
    private final ConfirmationTokenService confirmationTokenService;

    public QrController(QrService qrService, SecurityUtils securityUtils,
                         ConfirmationTokenService confirmationTokenService) {
        this.qrService = qrService;
        this.securityUtils = securityUtils;
        this.confirmationTokenService = confirmationTokenService;
    }

    /**
     * Procesa un pago escaneado desde QR. Requiere header {@code X-Confirmation-Token}
     * (mejora #22) obtenido de un {@code POST /api/v1/auth/pin/verify} exitoso.
     */
    @PostMapping("/pay")
    public ResponseEntity<Map<String, Object>> pay(
            @Valid @RequestBody QrPayRequest request,
            @RequestHeader(value = "X-Confirmation-Token", required = false) String confirmationToken) {
        String userId = securityUtils.getCurrentUserId();
        confirmationTokenService.validateAndConsume(confirmationToken, userId);
        return ResponseEntity.ok(qrService.processQrPayment(request));
    }

    /**
     * Decodifica un QR sin procesar pago.
     */
    @GetMapping("/decode")
    public ResponseEntity<Map<String, Object>> decode(@RequestParam("data") String data) {
        return ResponseEntity.ok(qrService.decodeQr(data));
    }
}
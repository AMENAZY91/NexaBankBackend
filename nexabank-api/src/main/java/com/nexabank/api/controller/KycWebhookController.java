package com.nexabank.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexabank.api.dto.kyc.KycWebhookPayload;
import com.nexabank.api.service.KycService;
import com.nexabank.api.service.provider.WebhookSignatureVerifier;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receptor de webhooks de un proveedor real de KYC — hoy sin tráfico real.
 *
 * <p>Público a nivel de Spring Security ({@code SecurityConfig}) porque un proveedor
 * externo no puede enviar un token Firebase; la única autenticación real es
 * {@link WebhookSignatureVerifier#verify}, que hoy rechaza siempre
 * ({@code NoOpWebhookSignatureVerifier}) porque no hay ningún secreto de proveedor real
 * configurado — ver docs/IMPLEMENTATION_PLAN.md Fase 6.
 */
@RestController
@RequestMapping("/api/v1/webhooks/kyc")
public class KycWebhookController {

    private final WebhookSignatureVerifier signatureVerifier;
    private final KycService kycService;
    private final ObjectMapper objectMapper;

    public KycWebhookController(WebhookSignatureVerifier signatureVerifier, KycService kycService,
                                 ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.kycService = kycService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signatureHeader,
            @Valid @RequestBody KycWebhookPayload payload) throws com.fasterxml.jackson.core.JsonProcessingException {
        signatureVerifier.verify(signatureHeader, objectMapper.writeValueAsString(payload));
        kycService.resolveFromWebhook(payload.getUserId(), payload.getVerificationId(),
                payload.getStatus(), payload.getRejectionReason());
        return ResponseEntity.ok().build();
    }
}

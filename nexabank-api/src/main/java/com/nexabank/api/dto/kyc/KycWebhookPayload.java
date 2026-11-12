package com.nexabank.api.dto.kyc;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload esperado desde {@code POST /api/v1/webhooks/kyc} — forma mínima que
 * necesitaría un proveedor real para notificar la resolución asíncrona de una
 * verificación. No tiene tráfico real todavía (ver {@code NoOpWebhookSignatureVerifier}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycWebhookPayload {

    @NotBlank(message = "verificationId is required")
    private String verificationId;

    @NotBlank(message = "userId is required")
    private String userId;

    @NotBlank(message = "status is required")
    private String status;

    private String rejectionReason;
}

package com.nexabank.api.service.provider;

import com.nexabank.api.exception.InvalidWebhookSignatureException;
import org.springframework.stereotype.Component;

/**
 * Implementación "fail-closed": como todavía no hay ningún proveedor real de KYC
 * configurado (ni, por lo tanto, ningún secreto HMAC que verificar), esta
 * implementación rechaza siempre. Deliberadamente NO es un no-op permisivo — un
 * verificador que "siempre pasa" en un endpoint público sería una vulnerabilidad real
 * (cualquiera podría aprobar/rechazar el KYC de cualquier usuario). El endpoint queda
 * así preparado en código (controller, DTO, lógica de resolución) pero inerte en la
 * práctica hasta que se reemplace este bean por una verificación HMAC real.
 */
@Component
public class NoOpWebhookSignatureVerifier implements WebhookSignatureVerifier {

    @Override
    public void verify(String signatureHeader, String rawBody) {
        throw new InvalidWebhookSignatureException(
                "No real KYC provider is configured yet; webhook signature verification is not implemented.");
    }
}

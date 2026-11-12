package com.nexabank.api.service.provider;

import com.nexabank.api.exception.InvalidWebhookSignatureException;

/**
 * Verifica la firma de un webhook entrante de un proveedor externo (ej. HMAC sobre el
 * body crudo con un secreto compartido). El endpoint que consume esto es público a
 * nivel de Spring Security (no hay token Firebase que un proveedor externo pueda
 * enviar) — esta verificación es la única capa de autenticación real.
 */
public interface WebhookSignatureVerifier {

    /**
     * @throws InvalidWebhookSignatureException si la firma es inválida, falta, o no
     *                                           hay ninguna forma de verificarla todavía
     */
    void verify(String signatureHeader, String rawBody);
}

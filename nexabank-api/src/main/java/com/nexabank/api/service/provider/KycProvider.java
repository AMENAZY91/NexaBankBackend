package com.nexabank.api.service.provider;

/**
 * Puerto hacia un proveedor externo de verificación de identidad (KYC).
 *
 * <p>Hoy la única implementación es {@link MockKycProvider}. El día que se conecte un
 * proveedor real (tipo Jumio/Onfido), se agrega una nueva implementación de esta
 * interfaz — {@link com.nexabank.api.service.KycService} no necesita cambiar.
 */
public interface KycProvider {

    /**
     * Envía una verificación al proveedor. {@link MockKycProvider} responde
     * sincrónicamente con {@code UNDER_REVIEW}; un proveedor real típicamente
     * respondería igual (la resolución final llega después, de forma asíncrona, vía
     * {@code KycWebhookController} — ver docs/IMPLEMENTATION_PLAN.md Fase 6).
     */
    KycSubmissionResult submit(KycSubmission submission);

    /**
     * Consulta el estado de una verificación por su ID. No se usa activamente desde
     * {@code KycService} en esta fase (la resolución llega vía admin o webhook, no por
     * polling) — existe para completar el contrato del puerto de cara a un proveedor
     * real que sí soporte esta consulta (ej. para un job de reconciliación futuro).
     */
    KycStatus getStatus(String verificationId);
}

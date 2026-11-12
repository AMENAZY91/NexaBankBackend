package com.nexabank.api.service.provider;

/**
 * Estado de verificación devuelto por un {@link KycProvider}. Deliberadamente no incluye
 * {@code SUBMITTED} ni {@code NOT_STARTED} — esos son estados del dominio propio
 * ({@code KycProfile}), no algo que un proveedor externo de verificación reportaría.
 */
public enum KycStatus {
    UNDER_REVIEW,
    APPROVED,
    REJECTED
}

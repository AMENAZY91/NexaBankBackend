package com.nexabank.api.service.provider;

/**
 * Datos de una verificación KYC a enviar al proveedor (ver {@link KycProvider}).
 */
public record KycSubmission(String userId, String documentType, String documentNumber,
                             String fullName, String dateOfBirth, String address) {
}

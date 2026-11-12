package com.nexabank.api.service.provider;

/**
 * Datos para emitir una tarjeta nueva (ver {@link CardIssuer#issue}).
 */
public record CardIssuanceRequest(String userId, String accountId) {
}

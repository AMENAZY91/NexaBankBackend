package com.nexabank.api.service.provider;

/**
 * Resultado de emitir una tarjeta: {@code token} es un identificador opaco de
 * correlación con el proveedor (no es el número de tarjeta, nunca lo contiene) —
 * {@code last4} es lo único que se muestra al usuario para identificar la tarjeta.
 */
public record IssuedCard(String token, String brand, String last4, int expiryMonth, int expiryYear) {
}

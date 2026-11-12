package com.nexabank.api.exception;

/**
 * Excepción lanzada cuando el header {@code X-Confirmation-Token} está ausente, no existe,
 * no pertenece al usuario autenticado, está expirado o ya fue usado (mejora #22).
 */
public class InvalidConfirmationTokenException extends RuntimeException {

    public InvalidConfirmationTokenException(String message) {
        super(message);
    }
}

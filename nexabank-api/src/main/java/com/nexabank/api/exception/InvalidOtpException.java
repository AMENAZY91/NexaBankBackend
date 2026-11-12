package com.nexabank.api.exception;

/**
 * Excepción lanzada cuando el código OTP de verificación telefónica falta, no existe,
 * está expirado, ya fue usado, o se superó el número máximo de intentos.
 */
public class InvalidOtpException extends RuntimeException {

    public InvalidOtpException(String message) {
        super(message);
    }
}

package com.nexabank.api.exception;

/**
 * Excepción lanzada cuando se requiere autenticación y no hay un usuario
 * autenticado en el contexto de seguridad. No se utiliza ningún identificador
 * de usuario ficticio (dummy) como fallback.
 */
public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException(String message) {
        super(message);
    }
}

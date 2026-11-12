package com.nexabank.api.exception;

/**
 * Excepción lanzada cuando se intenta registrar un usuario con un teléfono que ya existe.
 */
public class DuplicateUserException extends RuntimeException {

    public DuplicateUserException(String message) {
        super(message);
    }
}

package com.nexabank.api.exception;

public class PinLockedException extends RuntimeException {
    public PinLockedException(String message) {
        super(message);
    }
}

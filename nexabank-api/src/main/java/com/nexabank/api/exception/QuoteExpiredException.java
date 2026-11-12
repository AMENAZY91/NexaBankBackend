package com.nexabank.api.exception;

public class QuoteExpiredException extends RuntimeException {
    public QuoteExpiredException(String message) {
        super(message);
    }
}

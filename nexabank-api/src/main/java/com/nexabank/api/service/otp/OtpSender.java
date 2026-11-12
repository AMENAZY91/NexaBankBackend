package com.nexabank.api.service.otp;

/**
 * Abstrae el canal de envío del código OTP de verificación telefónica.
 */
public interface OtpSender {

    void send(String phoneE164, String code);
}

package com.nexabank.api.service.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Implementación de desarrollo/test: no envía SMS real, solo loguea el código y lo expone
 * para que los tests de integración puedan leerlo sin depender de un proveedor SMS real.
 */
@Component
@Profile({"dev", "test"})
public class LoggingOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

    private volatile String lastPhone;
    private volatile String lastCode;

    @Override
    public void send(String phoneE164, String code) {
        lastPhone = phoneE164;
        lastCode = code;
        log.info("[OTP-DEV] Código de verificación para {}: {}", phoneE164, code);
    }

    /** Solo para pruebas: devuelve el último código enviado a ese teléfono, o null. */
    public String getLastCodeFor(String phoneE164) {
        return phoneE164.equals(lastPhone) ? lastCode : null;
    }
}

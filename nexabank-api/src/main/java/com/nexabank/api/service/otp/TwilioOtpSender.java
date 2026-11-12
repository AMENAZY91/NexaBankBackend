package com.nexabank.api.service.otp;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Envía el OTP por SMS real vía Twilio. Requiere las variables de entorno
 * {@code TWILIO_ACCOUNT_SID}, {@code TWILIO_AUTH_TOKEN} y {@code TWILIO_FROM_NUMBER}.
 */
@Component
@Profile({"prod", "staging"})
public class TwilioOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(TwilioOtpSender.class);

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.from-number:}")
    private String fromNumber;

    @PostConstruct
    void init() {
        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            throw new IllegalStateException(
                    "Twilio credentials missing. Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_FROM_NUMBER.");
        }
        Twilio.init(accountSid, authToken);
    }

    @Override
    public void send(String phoneE164, String code) {
        Message.creator(
                        new PhoneNumber(phoneE164),
                        new PhoneNumber(fromNumber),
                        "Tu código de verificación NexaBank es: " + code)
                .create();
        log.info("OTP SMS sent via Twilio to {}", phoneE164);
    }
}

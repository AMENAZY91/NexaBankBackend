package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code phone_otps}, clave = número de teléfono.
 *
 * <p>Un nuevo {@code phone/start} sobrescribe el OTP anterior para el mismo teléfono
 * (invalidándolo). Vida útil: 5 minutos. Single-use. Máximo 5 intentos de verificación
 * antes de invalidarse por fuerza bruta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneOtp {

    private String phone;
    private String codeHash;
    private Instant expiresAt;

    @Builder.Default
    private int attempts = 0;

    @Builder.Default
    private boolean used = false;

    private Instant createdAt;
}

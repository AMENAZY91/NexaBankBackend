package com.nexabank.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para POST /api/v1/auth/pin/verify.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinVerifyRequest {

    @NotBlank(message = "PIN is required")
    @Pattern(regexp = "\\d{4,6}", message = "PIN must be 4-6 digits")
    private String pin;

    /**
     * Identificador de dispositivo opaco generado y persistido por el cliente
     * (ver docs/IMPLEMENTATION_PLAN.md, Fase 3). Opcional — si no se envía, el backend
     * simplemente no registra/actualiza el dispositivo, sin afectar la verificación del PIN.
     */
    private String deviceId;
    private String deviceName;
    private String platform;
}
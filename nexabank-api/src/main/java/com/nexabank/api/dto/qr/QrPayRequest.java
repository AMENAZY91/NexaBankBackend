package com.nexabank.api.dto.qr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para POST /api/v1/qr/pay.
 * Escaneo de código QR para pago.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrPayRequest {

    @NotBlank(message = "QR data is required")
    private String qrData;       // Datos del QR escaneado

    @NotBlank(message = "Amount is required")
    @Pattern(regexp = "\\d+(\\.\\d{1,2})?", message = "Amount must be a valid positive decimal number")
    private String amount;       // Monto a pagar

    private String currency;
    private String description;
}
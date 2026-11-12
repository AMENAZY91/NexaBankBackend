package com.nexabank.api.dto.paymentrequest;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de request para POST /api/v1/payment-requests/{id}/pay.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayPaymentRequestRequest {

    @NotBlank(message = "Source account ID is required")
    private String sourceAccountId;
}

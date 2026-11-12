package com.nexabank.api.dto.kyc;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de request para POST /api/v1/admin/kyc/{userId}/reject.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectKycRequest {

    @NotBlank(message = "Rejection reason is required")
    private String rejectionReason;
}

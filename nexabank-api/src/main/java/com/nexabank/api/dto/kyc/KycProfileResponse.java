package com.nexabank.api.dto.kyc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO de respuesta para perfiles KYC. Se devuelve completo (incluido
 * {@code documentNumber}) porque el único consumidor es el propio usuario
 * ({@code GET /api/v1/me/kyc}) o un admin ({@code GET /api/v1/admin/kyc/pending}) —
 * el enmascarado solo aplica a logs/auditoría, no a esta respuesta (ver
 * {@code KycService#maskDocumentNumber}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycProfileResponse {

    private String userId;
    private String status;
    private String documentType;
    private String documentNumber;
    private String fullName;
    private String dateOfBirth;
    private String address;
    private Instant submittedAt;
    private Instant reviewedAt;
    private String rejectionReason;
}

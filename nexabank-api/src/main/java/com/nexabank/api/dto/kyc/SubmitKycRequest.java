package com.nexabank.api.dto.kyc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de request para POST /api/v1/me/kyc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitKycRequest {

    @NotBlank(message = "Document type is required")
    @Pattern(regexp = "NATIONAL_ID|PASSPORT", message = "Document type must be one of: NATIONAL_ID, PASSPORT")
    private String documentType;

    @NotBlank(message = "Document number is required")
    private String documentNumber;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Date of birth is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date of birth must be in YYYY-MM-DD format")
    private String dateOfBirth;

    @NotBlank(message = "Address is required")
    private String address;
}

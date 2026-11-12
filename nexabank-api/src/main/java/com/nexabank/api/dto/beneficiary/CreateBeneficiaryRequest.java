package com.nexabank.api.dto.beneficiary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de request para POST /api/v1/beneficiaries.
 *
 * <p>Soporta datos internacionales (SWIFT/BIC, IBAN, país, banco, propósito).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBeneficiaryRequest {

    @NotBlank(message = "Beneficiary name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 50, message = "Alias must be at most 50 characters")
    private String alias;

    @NotBlank(message = "Account reference is required")
    private String accountReference;

    @Size(max = 3, message = "Country must be an ISO 3166-1 alpha-3 code")
    private String country;

    @Size(max = 100, message = "Bank name must be at most 100 characters")
    private String bankName;

    @Size(max = 11, message = "SWIFT/BIC must be 8 or 11 characters")
    private String swiftBic;

    @Size(max = 34, message = "IBAN must be at most 34 characters")
    private String iban;

    @Size(max = 200, message = "Bank address must be at most 200 characters")
    private String bankAddress;

    @Size(max = 100, message = "Purpose must be at most 100 characters")
    private String purpose;
}

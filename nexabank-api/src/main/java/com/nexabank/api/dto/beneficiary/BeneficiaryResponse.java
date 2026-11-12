package com.nexabank.api.dto.beneficiary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO de respuesta para beneficiarios.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BeneficiaryResponse {

    private String id;
    private String name;
    private String alias;
    private String accountReference;
    private String country;
    private String bankName;
    private String swiftBic;
    private String iban;
    private String bankAddress;
    private String purpose;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}

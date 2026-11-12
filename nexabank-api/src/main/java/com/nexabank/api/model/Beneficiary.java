package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code beneficiaries}.
 *
 * <p>Beneficiario con soporte para transferencias internacionales:
 * SWIFT/BIC, IBAN, país, banco y propósito del pago.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Beneficiary {

    private String id;
    private String userId;
    private String name;
    private String alias;
    private String accountReference;

    @Builder.Default
    private String status = "ACTIVE";

    private String country;
    private String bankName;
    private String swiftBic;
    private String iban;
    private String bankAddress;
    private String purpose;

    private Instant createdAt;
    private Instant updatedAt;
}

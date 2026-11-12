package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code kyc_profiles}.
 *
 * <p>A diferencia del resto de colecciones de este proyecto, el ID del documento es
 * directamente {@code userId} (no un ID generado) porque esta es una relación 1:1 por
 * diseño — cada usuario tiene a lo sumo un perfil KYC, así que no hace falta una
 * colección indexada por un ID propio para poder "upsert" sin antes consultar.
 *
 * <p>Estados: NOT_STARTED → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED. Ver
 * {@code service/provider/KycProvider} para la integración con el proveedor de
 * verificación (mock hoy, listo para uno real — ver docs/IMPLEMENTATION_PLAN.md Fase 6).
 *
 * <p>{@code documentNumber} es PII sensible — nunca se loguea ni se guarda en claro en
 * {@code AuditLog.metadata} (ver {@code KycService#maskDocumentNumber}), aunque sí se
 * devuelve completo al propio usuario en {@code GET /api/v1/me/kyc} (es su propio dato).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class KycProfile {

    private String userId;

    @Builder.Default
    private String status = "NOT_STARTED";

    private String verificationId;
    private String documentType;
    private String documentNumber;
    private String fullName;
    private String dateOfBirth;
    private String address;
    private Instant submittedAt;
    private Instant reviewedAt;
    private String reviewerId;
    private String rejectionReason;
}

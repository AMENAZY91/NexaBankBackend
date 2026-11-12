package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Entidad de dominio — documento Firestore {@code audit_logs}.
 *
 * <p>Toda operación financiera relevante genera auditoría.
 * No almacenar secretos ni tokens en {@code metadata}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    private String id;
    private String userId;
    private String action;
    private String resourceType;
    private String resourceId;
    private String status;
    private Instant timestamp;
    private Map<String, Object> metadata;
}

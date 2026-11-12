package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code devices}.
 *
 * <p>{@code deviceId} es el identificador opaco generado y persistido por el cliente
 * (ver docs/IMPLEMENTATION_PLAN.md, Fase 3); {@code id} es el ID propio del documento,
 * igual que en el resto de colecciones de este proyecto — nunca se usa un valor
 * provisto por el cliente directamente como ID de documento.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    private String id;
    private String userId;
    private String deviceId;
    private String deviceName;
    private String platform;

    @Builder.Default
    private boolean trusted = true;

    private Instant lastSeenAt;
    private Instant createdAt;
}

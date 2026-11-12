package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Entidad de dominio — documento Firestore {@code users}.
 *
 * <p>Esquema:
 * <pre>
 * {
 *   "id": "USR-000001",
 *   "firstName": "Juan",
 *   "lastName": "Perez",
 *   "documentId": "12345678",
 *   "address": "Calle 123 #45-67",
 *   "country": "CO",
 *   "email": "user@example.com",
 *   "displayName": "Juan Perez",
 *   "phone": "+573000000000",
 *   "status": "ACTIVE",
 *   "roles": ["USER"],
 *   "pin": "1234",
 *   "faceTemplate": null,
 *   "faceVerified": false,
 *   "biometricEnabled": false,
 *   "createdAt": "Timestamp",
 *   "updatedAt": "Timestamp",
 *   "lastLoginAt": "Timestamp"
 * }
 * </pre>
 *
 * <p>No almacenar contraseñas en Firestore si se utiliza Firebase Authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private String id;
    private String firstName;
    private String lastName;
    private String documentId;
    private String address;
    private String country;
    private String email;
    private String displayName;
    private String phone;
    private String status;

    @Builder.Default
    private List<String> roles = List.of("USER");

    private String pin;
    private Boolean biometricEnabled;
    private String faceTemplate;
    private Boolean faceVerified;

    /**
     * Bloqueo temporal tras demasiados PIN incorrectos (mejora de seguridad pre-producción,
     * ver docs/FRONTEND_BACKEND_INTEGRATION_PLAN.md Fase A) — mismo criterio que ya aplicaba
     * {@code PhoneOtp.attempts} para el OTP, pero sobre el propio documento de usuario porque
     * el PIN no tiene una entidad efímera propia como el OTP.
     */
    @Builder.Default
    private int pinFailedAttempts = 0;

    private Instant pinLockedUntil;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
}

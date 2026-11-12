package com.nexabank.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidad de dominio — documento Firestore {@code notifications}.
 *
 * <p>Se crea exclusivamente desde el backend como efecto secundario de otras
 * operaciones (transferencias, pagos QR, etc. — ver {@link com.nexabank.api.service.NotificationService#notify}).
 * No existe ningún endpoint público para crear notificaciones arbitrarias.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    private String id;
    private String userId;
    private String type;
    private String title;
    private String body;

    @Builder.Default
    private boolean read = false;

    private String relatedResourceType;
    private String relatedResourceId;
    private Instant createdAt;
}

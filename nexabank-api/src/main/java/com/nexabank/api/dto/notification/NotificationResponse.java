package com.nexabank.api.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO de respuesta para notificaciones.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private String id;
    private String type;
    private String title;
    private String body;
    private boolean read;
    private String relatedResourceType;
    private String relatedResourceId;
    private Instant createdAt;
}

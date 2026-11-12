package com.nexabank.api.mapper;

import com.nexabank.api.dto.notification.NotificationResponse;
import com.nexabank.api.model.Notification;
import org.springframework.stereotype.Component;

/**
 * Mapper para transformaciones Notification → DTO.
 */
@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .read(notification.isRead())
                .relatedResourceType(notification.getRelatedResourceType())
                .relatedResourceId(notification.getRelatedResourceId())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}

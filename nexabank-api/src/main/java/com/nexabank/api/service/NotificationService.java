package com.nexabank.api.service;

import com.nexabank.api.dto.notification.NotificationResponse;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.NotificationMapper;
import com.nexabank.api.model.Notification;
import com.nexabank.api.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Servicio de notificaciones.
 *
 * <p>{@link #notify} es de uso interno — llamado como efecto secundario de otras
 * operaciones (ver {@code TransferService}, {@code QrService}). No existe ningún
 * endpoint público que permita crear una notificación arbitraria.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationRepository notificationRepository,
                                NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
    }

    public void notify(String userId, String type, String title, String body,
                        String relatedResourceType, String relatedResourceId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .read(false)
                .relatedResourceType(relatedResourceType)
                .relatedResourceId(relatedResourceId)
                .createdAt(Instant.now())
                .build();
        Notification saved = notificationRepository.save(notification);
        log.info("Notification {} ({}) created for user {}", saved.getId(), type, userId);
    }

    /**
     * Página de notificaciones de un usuario: los elementos y el cursor para la
     * siguiente página, o {@code null} si no hay más resultados.
     */
    public record NotificationCursorPage(List<NotificationResponse> items, String nextCursor) {
    }

    public NotificationCursorPage getNotifications(String userId, String cursor, int limit) {
        NotificationRepository.CursorPage page = notificationRepository.findByUserIdAfter(userId, cursor, limit);
        List<NotificationResponse> items = page.items().stream()
                .map(notificationMapper::toResponse)
                .toList();
        return new NotificationCursorPage(items, page.nextCursor());
    }

    public void markAsRead(String id, String userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with ID: " + id));

        if (userId != null && !userId.equals(notification.getUserId())) {
            throw new UnauthorizedOperationException("You do not have access to this notification");
        }

        notificationRepository.markRead(id);
    }
}

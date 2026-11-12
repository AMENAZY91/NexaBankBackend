package com.nexabank.api.controller;

import com.nexabank.api.dto.common.PageResponse;
import com.nexabank.api.dto.notification.NotificationResponse;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityUtils securityUtils;

    public NotificationController(NotificationService notificationService, SecurityUtils securityUtils) {
        this.notificationService = notificationService;
        this.securityUtils = securityUtils;
    }

    /**
     * Paginación por cursor real de Firestore (mismo patrón que
     * {@code GET /api/v1/transactions} — ver {@code TransactionController}). No existe
     * un modo page/size en memoria para este endpoint porque es nuevo: no hay un
     * contrato histórico que preservar.
     */
    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> getNotifications(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        String userId = securityUtils.getCurrentUserId();
        NotificationService.NotificationCursorPage cursorPage =
                notificationService.getNotifications(userId, cursor, size);

        PageResponse<NotificationResponse> pageResponse = PageResponse.<NotificationResponse>builder()
                .data(cursorPage.items())
                .page(0)
                .size(size)
                .totalElements(cursorPage.items().size())
                .totalPages(cursorPage.nextCursor() != null ? -1 : 1)
                .nextCursor(cursorPage.nextCursor())
                .build();
        return ResponseEntity.ok(pageResponse);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        notificationService.markAsRead(id, userId);
        return ResponseEntity.noContent().build();
    }
}

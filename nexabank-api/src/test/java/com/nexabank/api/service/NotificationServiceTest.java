package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.NotificationMapper;
import com.nexabank.api.model.Notification;
import com.nexabank.api.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, new NotificationMapper());
    }

    @Test
    void notify_shouldSaveNotification_withExpectedFields() {
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.notify("USR-000001", "TRANSFER_SENT", "Transferencia enviada",
                "Enviaste 100 COP a Juan", "TRANSFER", "TRF-000001");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo("USR-000001");
        assertThat(saved.getType()).isEqualTo("TRANSFER_SENT");
        assertThat(saved.getTitle()).isEqualTo("Transferencia enviada");
        assertThat(saved.getBody()).isEqualTo("Enviaste 100 COP a Juan");
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getRelatedResourceType()).isEqualTo("TRANSFER");
        assertThat(saved.getRelatedResourceId()).isEqualTo("TRF-000001");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void getNotifications_shouldMapCursorPage() {
        Notification notification = Notification.builder()
                .id("NOT-00000001").userId("USR-000001").type("TRANSFER_SENT")
                .title("Transferencia enviada").body("...").read(false)
                .createdAt(Instant.now()).build();
        when(notificationRepository.findByUserIdAfter("USR-000001", null, 20))
                .thenReturn(new NotificationRepository.CursorPage(List.of(notification), "NOT-00000001"));

        NotificationService.NotificationCursorPage page =
                notificationService.getNotifications("USR-000001", null, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).getId()).isEqualTo("NOT-00000001");
        assertThat(page.nextCursor()).isEqualTo("NOT-00000001");
    }

    @Test
    void markAsRead_shouldSucceed_whenOwner() {
        Notification notification = Notification.builder()
                .id("NOT-00000001").userId("USR-000001").read(false).build();
        when(notificationRepository.findById("NOT-00000001")).thenReturn(Optional.of(notification));

        notificationService.markAsRead("NOT-00000001", "USR-000001");

        verify(notificationRepository).markRead("NOT-00000001");
    }

    @Test
    void markAsRead_shouldThrowNotFound_whenMissing() {
        when(notificationRepository.findById("NOT-999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead("NOT-999999", "USR-000001"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAsRead_shouldThrowUnauthorized_whenNotOwner() {
        Notification notification = Notification.builder()
                .id("NOT-00000001").userId("USR-000001").read(false).build();
        when(notificationRepository.findById("NOT-00000001")).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead("NOT-00000001", "USR-000002"))
                .isInstanceOf(UnauthorizedOperationException.class);
    }
}

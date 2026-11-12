package com.nexabank.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.firebase.auth.FirebaseAuth;
import com.nexabank.api.dto.notification.NotificationResponse;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.NotificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

// addFilters = false: estos tests ejercitan solo la capa web (controller + DTOs),
// no la cadena de filtros de Spring Security (ya cubierta por tests de SecurityConfig).
@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    // Requerido para que el contexto cargue: SecurityConfig (cargado por
    // SecurityAutoConfiguration en el slice @WebMvcTest) instancia FirebaseAuthenticationFilter,
    // que depende de este bean.
    @MockBean
    private FirebaseAuth firebaseAuth;

    @MockBean
    private SecurityUtils securityUtils;

    @Test
    void getNotifications_shouldReturnPageWithNextCursor() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        NotificationResponse notification = NotificationResponse.builder()
                .id("NOT-00000001").type("TRANSFER_SENT").title("Transferencia enviada")
                .body("...").read(false).createdAt(Instant.now()).build();
        when(notificationService.getNotifications("USR-000001", null, 20))
                .thenReturn(new NotificationService.NotificationCursorPage(List.of(notification), "NOT-00000001"));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("NOT-00000001"))
                .andExpect(jsonPath("$.nextCursor").value("NOT-00000001"));
    }

    @Test
    void getNotifications_shouldReturnEmptyPage_withoutNextCursor_whenNoMoreResults() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(notificationService.getNotifications("USR-000001", null, 20))
                .thenReturn(new NotificationService.NotificationCursorPage(List.of(), null));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void markAsRead_shouldReturnNoContent() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");

        mockMvc.perform(patch("/api/v1/notifications/NOT-00000001/read"))
                .andExpect(status().isNoContent());
    }
}

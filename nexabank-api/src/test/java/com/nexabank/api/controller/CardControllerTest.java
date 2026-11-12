package com.nexabank.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.nexabank.api.dto.card.CardResponse;
import com.nexabank.api.dto.card.CreateCardRequest;
import com.nexabank.api.dto.card.UpdateCardRequest;
import com.nexabank.api.exception.InvalidConfirmationTokenException;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.CardService;
import com.nexabank.api.service.ConfirmationTokenService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// addFilters = false: estos tests ejercitan solo la capa web (controller + DTOs),
// no la cadena de filtros de Spring Security (ya cubierta por tests de SecurityConfig).
@WebMvcTest(CardController.class)
@AutoConfigureMockMvc(addFilters = false)
class CardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CardService cardService;

    @MockBean
    private SecurityUtils securityUtils;

    @MockBean
    private ConfirmationTokenService confirmationTokenService;

    // Requerido para que el contexto cargue: SecurityConfig (cargado por
    // SecurityAutoConfiguration en el slice @WebMvcTest) instancia FirebaseAuthenticationFilter,
    // que depende de este bean.
    @MockBean
    private FirebaseAuth firebaseAuth;

    @Test
    void issueCard_shouldReturnCreated_whenConfirmationTokenValid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(cardService.issue(any(), eq("USR-000001")))
                .thenReturn(CardResponse.builder().id("CARD-00000001").last4("5678").status("ACTIVE").build());

        CreateCardRequest request = CreateCardRequest.builder().accountId("ACC-000001").build();

        mockMvc.perform(post("/api/v1/cards")
                        .header("X-Confirmation-Token", "valid-confirmation-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.last4").value("5678"));

        verify(confirmationTokenService).validateAndConsume("valid-confirmation-token", "USR-000001");
    }

    @Test
    void issueCard_shouldReturnUnauthorized_whenConfirmationTokenInvalid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        doThrow(new InvalidConfirmationTokenException("Invalid confirmation token"))
                .when(confirmationTokenService).validateAndConsume("bad-token", "USR-000001");

        CreateCardRequest request = CreateCardRequest.builder().accountId("ACC-000001").build();

        mockMvc.perform(post("/api/v1/cards")
                        .header("X-Confirmation-Token", "bad-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCards_shouldReturnList() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(cardService.getCardsByUser("USR-000001")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void updateCard_shouldReturnBlocked() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(cardService.update(eq("CARD-00000001"), any(), eq("USR-000001")))
                .thenReturn(CardResponse.builder().id("CARD-00000001").status("BLOCKED").build());

        UpdateCardRequest request = UpdateCardRequest.builder().status("BLOCKED").build();

        mockMvc.perform(patch("/api/v1/cards/CARD-00000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void updateCard_shouldReturnBadRequest_whenStatusInvalid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");

        mockMvc.perform(patch("/api/v1/cards/CARD-00000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelCard_shouldReturnNoContent_whenConfirmationTokenValid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");

        mockMvc.perform(delete("/api/v1/cards/CARD-00000001")
                        .header("X-Confirmation-Token", "valid-confirmation-token"))
                .andExpect(status().isNoContent());

        verify(confirmationTokenService).validateAndConsume("valid-confirmation-token", "USR-000001");
    }

    @Test
    void cancelCard_shouldReturnUnauthorized_whenConfirmationTokenInvalid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        doThrow(new InvalidConfirmationTokenException("Invalid confirmation token"))
                .when(confirmationTokenService).validateAndConsume("bad-token", "USR-000001");

        mockMvc.perform(delete("/api/v1/cards/CARD-00000001")
                        .header("X-Confirmation-Token", "bad-token"))
                .andExpect(status().isUnauthorized());
    }
}

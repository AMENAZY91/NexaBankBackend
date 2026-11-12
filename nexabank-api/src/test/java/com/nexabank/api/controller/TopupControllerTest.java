package com.nexabank.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.nexabank.api.dto.topup.CreateTopupRequest;
import com.nexabank.api.dto.topup.TopupResponse;
import com.nexabank.api.exception.InvalidConfirmationTokenException;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.ConfirmationTokenService;
import com.nexabank.api.service.TopupService;
import java.math.BigDecimal;
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
@WebMvcTest(TopupController.class)
@AutoConfigureMockMvc(addFilters = false)
class TopupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TopupService topupService;

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
    void createTopup_shouldReturnCreated_whenValid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(topupService.createTopup(any(), any(), eq("USR-000001")))
                .thenReturn(TopupResponse.builder().id("TOP-00000001").status("COMPLETED").build());

        CreateTopupRequest request = CreateTopupRequest.builder()
                .accountId("ACC-000001").amount(new BigDecimal("50000.00"))
                .currency("COP").source("CARD").build();

        mockMvc.perform(post("/api/v1/topups")
                        .header("X-Confirmation-Token", "valid-confirmation-token")
                        .header("Idempotency-Key", "idem-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("TOP-00000001"));

        verify(confirmationTokenService).validateAndConsume("valid-confirmation-token", "USR-000001");
    }

    @Test
    void createTopup_shouldReturnUnauthorized_whenConfirmationTokenInvalid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        doThrow(new InvalidConfirmationTokenException("Invalid confirmation token"))
                .when(confirmationTokenService).validateAndConsume("bad-token", "USR-000001");

        CreateTopupRequest request = CreateTopupRequest.builder()
                .accountId("ACC-000001").amount(new BigDecimal("50000.00"))
                .currency("COP").source("CARD").build();

        mockMvc.perform(post("/api/v1/topups")
                        .header("X-Confirmation-Token", "bad-token")
                        .header("Idempotency-Key", "idem-key-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTopup_shouldReturnBadRequest_whenSourceInvalid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");

        CreateTopupRequest request = CreateTopupRequest.builder()
                .accountId("ACC-000001").amount(new BigDecimal("50000.00"))
                .currency("COP").source("BITCOIN").build();

        mockMvc.perform(post("/api/v1/topups")
                        .header("X-Confirmation-Token", "valid-confirmation-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTopup_shouldReturnBadRequest_whenIdempotencyKeyMissing() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");

        CreateTopupRequest request = CreateTopupRequest.builder()
                .accountId("ACC-000001").amount(new BigDecimal("50000.00"))
                .currency("COP").source("CARD").build();

        mockMvc.perform(post("/api/v1/topups")
                        .header("X-Confirmation-Token", "valid-confirmation-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTopups_shouldReturnList() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(topupService.getTopupsByUser("USR-000001")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/topups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getTopup_shouldReturnTopup() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(topupService.getTopupById("TOP-00000001", "USR-000001"))
                .thenReturn(TopupResponse.builder().id("TOP-00000001").build());

        mockMvc.perform(get("/api/v1/topups/TOP-00000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("TOP-00000001"));
    }
}

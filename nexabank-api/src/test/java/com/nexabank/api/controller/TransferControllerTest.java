package com.nexabank.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.nexabank.api.dto.transfer.CreateTransferRequest;
import com.nexabank.api.dto.transfer.TransferResponse;
import com.nexabank.api.exception.InvalidConfirmationTokenException;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.ConfirmationTokenService;
import com.nexabank.api.service.TransferService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

// addFilters = false: estos tests ejercitan solo la capa web (controller + DTOs),
// no la cadena de filtros de Spring Security (ya cubierta por tests de SecurityConfig).
@WebMvcTest(TransferController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferService transferService;

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
    void createTransfer_shouldReturnCreated_whenValid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(transferService.executeTransfer(any(), any(), eq("USR-000001")))
                .thenReturn(TransferResponse.builder()
                        .id("TRF-000001").status("COMPLETED")
                        .amount(new BigDecimal("200000.00")).currency("COP").build());

        CreateTransferRequest request = CreateTransferRequest.builder()
                .sourceAccountId("ACC-000001").destinationAccountId("ACC-000002")
                .amount(new BigDecimal("200000.00"))
                .sourceCurrency("COP").destinationCurrency("COP").currency("COP")
                .build();

        mockMvc.perform(post("/api/v1/transfers")
                        .header("X-Confirmation-Token", "valid-confirmation-token")
                        .header("Idempotency-Key", "idem-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("TRF-000001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(confirmationTokenService).validateAndConsume("valid-confirmation-token", "USR-000001");
    }

    @Test
    void createTransfer_shouldReturnUnauthorized_whenConfirmationTokenInvalid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        doThrow(new InvalidConfirmationTokenException("Invalid confirmation token"))
                .when(confirmationTokenService).validateAndConsume("bad-token", "USR-000001");

        CreateTransferRequest request = CreateTransferRequest.builder()
                .sourceAccountId("ACC-000001").destinationAccountId("ACC-000002")
                .amount(new BigDecimal("200000.00"))
                .sourceCurrency("COP").destinationCurrency("COP").currency("COP")
                .build();

        mockMvc.perform(post("/api/v1/transfers")
                        .header("X-Confirmation-Token", "bad-token")
                        .header("Idempotency-Key", "idem-key-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTransfer_shouldReturnBadRequest_whenIdempotencyKeyMissing() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");

        CreateTransferRequest request = CreateTransferRequest.builder()
                .sourceAccountId("ACC-000001").destinationAccountId("ACC-000002")
                .amount(new BigDecimal("200000.00"))
                .sourceCurrency("COP").destinationCurrency("COP").currency("COP")
                .build();

        mockMvc.perform(post("/api/v1/transfers")
                        .header("X-Confirmation-Token", "valid-confirmation-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTransfers_shouldReturnList() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(transferService.getTransfersByUser("USR-000001")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getTransfer_shouldReturnTransfer() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(transferService.getTransferById("TRF-000001", "USR-000001"))
                .thenReturn(TransferResponse.builder().id("TRF-000001").build());

        mockMvc.perform(get("/api/v1/transfers/TRF-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("TRF-000001"));
    }

    @Test
    void cancelTransfer_shouldReturnCancelled() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(transferService.cancelTransfer("TRF-000001", "USR-000001"))
                .thenReturn(TransferResponse.builder().id("TRF-000001").status("CANCELLED").build());

        mockMvc.perform(post("/api/v1/transfers/TRF-000001/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}

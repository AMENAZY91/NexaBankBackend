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
import com.nexabank.api.dto.paymentrequest.CreatePaymentRequestRequest;
import com.nexabank.api.dto.paymentrequest.PayPaymentRequestRequest;
import com.nexabank.api.dto.paymentrequest.PaymentRequestResponse;
import com.nexabank.api.exception.InvalidConfirmationTokenException;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.ConfirmationTokenService;
import com.nexabank.api.service.PaymentRequestService;
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
@WebMvcTest(PaymentRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentRequestService paymentRequestService;

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
    void createPaymentRequest_shouldReturnCreated() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-REQUESTER");
        when(paymentRequestService.create(any(), eq("USR-REQUESTER")))
                .thenReturn(PaymentRequestResponse.builder().id("PRQ-00000001").status("PENDING").build());

        CreatePaymentRequestRequest request = CreatePaymentRequestRequest.builder()
                .payerPhone("+573000000000").requesterAccountId("ACC-REQUESTER")
                .amount(new BigDecimal("50000.00")).currency("COP").build();

        mockMvc.perform(post("/api/v1/payment-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("PRQ-00000001"));
    }

    @Test
    void getPaymentRequests_shouldDefaultToReceived() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(paymentRequestService.getByUser("USR-000001", "received")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/payment-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getPaymentRequests_shouldUseSentRole_whenSpecified() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(paymentRequestService.getByUser("USR-000001", "sent")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/payment-requests").param("role", "sent"))
                .andExpect(status().isOk());

        verify(paymentRequestService).getByUser("USR-000001", "sent");
    }

    @Test
    void pay_shouldReturnPaid_whenConfirmationTokenValid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-PAYER");
        when(paymentRequestService.pay(eq("PRQ-00000001"), any(), any(), eq("USR-PAYER")))
                .thenReturn(PaymentRequestResponse.builder().id("PRQ-00000001").status("PAID").build());

        PayPaymentRequestRequest payRequest = PayPaymentRequestRequest.builder().sourceAccountId("ACC-PAYER").build();

        mockMvc.perform(post("/api/v1/payment-requests/PRQ-00000001/pay")
                        .header("X-Confirmation-Token", "valid-confirmation-token")
                        .header("Idempotency-Key", "idem-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        verify(confirmationTokenService).validateAndConsume("valid-confirmation-token", "USR-PAYER");
    }

    @Test
    void pay_shouldReturnUnauthorized_whenConfirmationTokenInvalid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-PAYER");
        doThrow(new InvalidConfirmationTokenException("Invalid confirmation token"))
                .when(confirmationTokenService).validateAndConsume("bad-token", "USR-PAYER");

        PayPaymentRequestRequest payRequest = PayPaymentRequestRequest.builder().sourceAccountId("ACC-PAYER").build();

        mockMvc.perform(post("/api/v1/payment-requests/PRQ-00000001/pay")
                        .header("X-Confirmation-Token", "bad-token")
                        .header("Idempotency-Key", "idem-key-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pay_shouldReturnBadRequest_whenIdempotencyKeyMissing() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-PAYER");

        PayPaymentRequestRequest payRequest = PayPaymentRequestRequest.builder().sourceAccountId("ACC-PAYER").build();

        mockMvc.perform(post("/api/v1/payment-requests/PRQ-00000001/pay")
                        .header("X-Confirmation-Token", "valid-confirmation-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void decline_shouldReturnDeclined() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-PAYER");
        when(paymentRequestService.decline("PRQ-00000001", "USR-PAYER"))
                .thenReturn(PaymentRequestResponse.builder().id("PRQ-00000001").status("DECLINED").build());

        mockMvc.perform(post("/api/v1/payment-requests/PRQ-00000001/decline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void cancel_shouldReturnCancelled() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-REQUESTER");
        when(paymentRequestService.cancel("PRQ-00000001", "USR-REQUESTER"))
                .thenReturn(PaymentRequestResponse.builder().id("PRQ-00000001").status("CANCELLED").build());

        mockMvc.perform(post("/api/v1/payment-requests/PRQ-00000001/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}

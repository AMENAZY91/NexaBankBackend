package com.nexabank.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.nexabank.api.dto.kyc.KycWebhookPayload;
import com.nexabank.api.exception.InvalidWebhookSignatureException;
import com.nexabank.api.service.KycService;
import com.nexabank.api.service.provider.WebhookSignatureVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// addFilters = false: este endpoint es público a nivel de Spring Security (ver
// SecurityConfig); este test cubre solo la capa web + la verificación de firma.
@WebMvcTest(KycWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class KycWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebhookSignatureVerifier signatureVerifier;

    @MockBean
    private KycService kycService;

    // Requerido para que el contexto cargue: SecurityConfig (cargado por
    // SecurityAutoConfiguration en el slice @WebMvcTest) instancia FirebaseAuthenticationFilter,
    // que depende de este bean.
    @MockBean
    private FirebaseAuth firebaseAuth;

    @Test
    void handleWebhook_shouldReturnUnauthorized_whenSignatureInvalid() throws Exception {
        doThrow(new InvalidWebhookSignatureException("Invalid signature"))
                .when(signatureVerifier).verify(any(), any());

        KycWebhookPayload payload = KycWebhookPayload.builder()
                .verificationId("VER-ABC123").userId("USR-000001").status("APPROVED").build();

        mockMvc.perform(post("/api/v1/webhooks/kyc")
                        .header("X-Webhook-Signature", "sha256=whatever")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(kycService);
    }

    @Test
    void handleWebhook_shouldResolveAndReturnOk_whenSignatureValid() throws Exception {
        KycWebhookPayload payload = KycWebhookPayload.builder()
                .verificationId("VER-ABC123").userId("USR-000001").status("APPROVED").build();

        mockMvc.perform(post("/api/v1/webhooks/kyc")
                        .header("X-Webhook-Signature", "sha256=whatever")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        verify(kycService).resolveFromWebhook("USR-000001", "VER-ABC123", "APPROVED", null);
    }

    @Test
    void handleWebhook_shouldReturnBadRequest_whenUserIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/kyc")
                        .header("X-Webhook-Signature", "sha256=whatever")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationId\":\"VER-ABC123\",\"status\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(kycService);
    }
}

package com.nexabank.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.nexabank.api.dto.kyc.KycProfileResponse;
import com.nexabank.api.dto.kyc.SubmitKycRequest;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.KycService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// addFilters = false: estos tests ejercitan solo la capa web (controller + DTOs),
// no la cadena de filtros de Spring Security (ya cubierta por tests de SecurityConfig).
@WebMvcTest(KycController.class)
@AutoConfigureMockMvc(addFilters = false)
class KycControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KycService kycService;

    @MockBean
    private SecurityUtils securityUtils;

    // Requerido para que el contexto cargue: SecurityConfig (cargado por
    // SecurityAutoConfiguration en el slice @WebMvcTest) instancia FirebaseAuthenticationFilter,
    // que depende de este bean.
    @MockBean
    private FirebaseAuth firebaseAuth;

    @Test
    void getMyKyc_shouldReturnStatus() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(kycService.getMyProfile("USR-000001"))
                .thenReturn(KycProfileResponse.builder().userId("USR-000001").status("NOT_STARTED").build());

        mockMvc.perform(get("/api/v1/me/kyc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"));
    }

    @Test
    void submitKyc_shouldReturnUnderReview_whenValid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(kycService.submit(any(), eq("USR-000001")))
                .thenReturn(KycProfileResponse.builder().userId("USR-000001").status("UNDER_REVIEW").build());

        SubmitKycRequest request = SubmitKycRequest.builder()
                .documentType("NATIONAL_ID").documentNumber("12345678")
                .fullName("Juan Perez").dateOfBirth("1990-01-01").address("Calle 123").build();

        mockMvc.perform(post("/api/v1/me/kyc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    void submitKyc_shouldReturnBadRequest_whenDocumentTypeInvalid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");

        SubmitKycRequest request = SubmitKycRequest.builder()
                .documentType("DRIVER_LICENSE").documentNumber("12345678")
                .fullName("Juan Perez").dateOfBirth("1990-01-01").address("Calle 123").build();

        mockMvc.perform(post("/api/v1/me/kyc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitKyc_shouldReturnBadRequest_whenDateOfBirthMalformed() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");

        SubmitKycRequest request = SubmitKycRequest.builder()
                .documentType("NATIONAL_ID").documentNumber("12345678")
                .fullName("Juan Perez").dateOfBirth("01/01/1990").address("Calle 123").build();

        mockMvc.perform(post("/api/v1/me/kyc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

package com.nexabank.api.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.nexabank.api.dto.kyc.KycProfileResponse;
import com.nexabank.api.dto.kyc.RejectKycRequest;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.KycService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// addFilters = false: la exigencia del rol ADMIN vía @PreAuthorize ya se prueba con la
// cadena de filtros real en SecurityConfigTest; este test cubre solo la capa web.
@WebMvcTest(AdminKycController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminKycControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KycService kycService;

    @MockBean
    private SecurityUtils securityUtils;

    @MockBean
    private FirebaseAuth firebaseAuth;

    @Test
    void getPending_shouldReturnList() throws Exception {
        when(kycService.getPending()).thenReturn(
                List.of(KycProfileResponse.builder().userId("USR-000001").status("SUBMITTED").build()));

        mockMvc.perform(get("/api/v1/admin/kyc/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("USR-000001"));
    }

    @Test
    void approve_shouldReturnApproved() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("ADMIN-001");
        when(kycService.approve("USR-000001", "ADMIN-001"))
                .thenReturn(KycProfileResponse.builder().userId("USR-000001").status("APPROVED").build());

        mockMvc.perform(post("/api/v1/admin/kyc/USR-000001/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void reject_shouldReturnRejected_withReason() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("ADMIN-001");
        when(kycService.reject(eq("USR-000001"), eq("ADMIN-001"), eq("Documento ilegible")))
                .thenReturn(KycProfileResponse.builder().userId("USR-000001").status("REJECTED")
                        .rejectionReason("Documento ilegible").build());

        RejectKycRequest request = RejectKycRequest.builder().rejectionReason("Documento ilegible").build();

        mockMvc.perform(post("/api/v1/admin/kyc/USR-000001/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectionReason").value("Documento ilegible"));
    }

    @Test
    void reject_shouldReturnBadRequest_whenReasonMissing() throws Exception {
        mockMvc.perform(post("/api/v1/admin/kyc/USR-000001/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}

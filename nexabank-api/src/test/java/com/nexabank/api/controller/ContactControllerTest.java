package com.nexabank.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.nexabank.api.dto.beneficiary.BeneficiaryResponse;
import com.nexabank.api.dto.beneficiary.CreateBeneficiaryRequest;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.BeneficiaryService;
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
@WebMvcTest(ContactController.class)
@AutoConfigureMockMvc(addFilters = false)
class ContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BeneficiaryService beneficiaryService;

    // Requerido para que el contexto cargue: SecurityConfig (cargado por
    // SecurityAutoConfiguration en el slice @WebMvcTest) instancia FirebaseAuthenticationFilter,
    // que depende de este bean.
    @MockBean
    private FirebaseAuth firebaseAuth;

    @MockBean
    private SecurityUtils securityUtils;

    @Test
    void getContacts_shouldReturnList() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(beneficiaryService.getBeneficiariesByUser("USR-000001")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getContact_shouldReturnContact() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(beneficiaryService.getBeneficiaryById("BEN-000001", "USR-000001"))
                .thenReturn(BeneficiaryResponse.builder().id("BEN-000001").build());

        mockMvc.perform(get("/api/v1/contacts/BEN-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("BEN-000001"));
    }

    @Test
    void createContact_shouldReturnCreated_whenValid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(beneficiaryService.createBeneficiary(any(), eq("USR-000001")))
                .thenReturn(BeneficiaryResponse.builder().id("BEN-000001").name("Carlos Pérez").build());

        CreateBeneficiaryRequest request = CreateBeneficiaryRequest.builder()
                .name("Carlos Pérez").accountReference("ACC-000002").build();

        mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("BEN-000001"));
    }

    @Test
    void createContact_shouldReturnBadRequest_whenNameMissing() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");

        CreateBeneficiaryRequest request = CreateBeneficiaryRequest.builder()
                .accountReference("ACC-000002").build();

        mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateContact_shouldReturnUpdated() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(beneficiaryService.updateBeneficiary(eq("BEN-000001"), any(), eq("USR-000001")))
                .thenReturn(BeneficiaryResponse.builder().id("BEN-000001").build());

        mockMvc.perform(put("/api/v1/contacts/BEN-000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Carlos Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("BEN-000001"));
    }

    @Test
    void deleteContact_shouldReturnNoContent() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");

        mockMvc.perform(delete("/api/v1/contacts/BEN-000001"))
                .andExpect(status().isNoContent());
    }
}

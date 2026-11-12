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
import com.nexabank.api.dto.account.AccountResponse;
import com.nexabank.api.dto.account.BalanceResponse;
import com.nexabank.api.dto.account.CreateAccountRequest;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.AccountService;
import java.math.BigDecimal;
import java.time.Instant;
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
@WebMvcTest(WalletController.class)
@AutoConfigureMockMvc(addFilters = false)
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    // Requerido para que el contexto cargue: SecurityConfig (cargado por
    // SecurityAutoConfiguration en el slice @WebMvcTest) instancia FirebaseAuthenticationFilter,
    // que depende de este bean.
    @MockBean
    private FirebaseAuth firebaseAuth;

    @MockBean
    private SecurityUtils securityUtils;

    @Test
    void getWallets_shouldReturnList() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(accountService.getAccountsByUser("USR-000001")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/wallets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getWallet_shouldReturnWallet() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(accountService.getAccountById("ACC-000001", "USR-000001"))
                .thenReturn(AccountResponse.builder().id("ACC-000001").name("Cuenta principal").build());

        mockMvc.perform(get("/api/v1/wallets/ACC-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ACC-000001"));
    }

    @Test
    void getWalletBalance_shouldReturnBalance() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(accountService.getBalance("ACC-000001", "USR-000001"))
                .thenReturn(BalanceResponse.builder()
                        .balance(new BigDecimal("1500000.00"))
                        .currency("COP")
                        .updatedAt(Instant.now())
                        .build());

        mockMvc.perform(get("/api/v1/wallets/ACC-000001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1500000.00))
                .andExpect(jsonPath("$.currency").value("COP"));
    }

    @Test
    void createWallet_shouldReturnCreated_whenValid() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(accountService.createAccount(any(), eq("USR-000001")))
                .thenReturn(AccountResponse.builder().id("ACC-000002").type("SAVINGS").currency("COP").build());

        CreateAccountRequest request = CreateAccountRequest.builder()
                .type("SAVINGS").currency("COP").name("Cuenta nueva").build();

        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ACC-000002"));
    }

    @Test
    void createWallet_shouldReturnBadRequest_whenTypeMissing() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");

        CreateAccountRequest request = CreateAccountRequest.builder()
                .currency("COP").name("Cuenta nueva").build();

        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

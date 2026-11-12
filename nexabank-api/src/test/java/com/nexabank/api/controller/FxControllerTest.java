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
import com.nexabank.api.dto.fx.ConvertCurrencyRequest;
import com.nexabank.api.dto.fx.ExchangeRateResponse;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.FeeService;
import com.nexabank.api.service.FxService;
import com.nexabank.api.service.TransferService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// addFilters = false: estos tests ejercitan solo la capa web (controller + DTOs),
// no la cadena de filtros de Spring Security (ya cubierta por tests de SecurityConfig).
@WebMvcTest(FxController.class)
@AutoConfigureMockMvc(addFilters = false)
class FxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FxService fxService;

    @MockBean
    private FeeService feeService;

    @MockBean
    private TransferService transferService;

    @MockBean
    private SecurityUtils securityUtils;

    // Requerido para que el contexto cargue: FirebaseAuthenticationFilter/RateLimitFilter
    // son beans Filter que Spring Boot auto-registra como filtros de servlet
    // independientemente de la cadena de Spring Security; FirebaseAuthenticationFilter
    // depende de este bean.
    @MockBean
    private FirebaseAuth firebaseAuth;

    @Test
    void getRate_shouldReturnRate() throws Exception {
        when(fxService.getRate("USD", "COP")).thenReturn(ExchangeRateResponse.builder()
                .baseCurrency("USD").targetCurrency("COP")
                .rate(new BigDecimal("4000.00"))
                .timestamp(Instant.now()).source("fallback").build());

        mockMvc.perform(get("/api/v1/fx/rates/USD/COP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.targetCurrency").value("COP"))
                .andExpect(jsonPath("$.rate").value(4000.00));
    }

    @Test
    void convert_shouldReturnConversion() throws Exception {
        when(fxService.convert(any())).thenReturn(com.nexabank.api.dto.fx.ConvertCurrencyResponse.builder()
                .sourceCurrency("USD").targetCurrency("COP")
                .sourceAmount(new BigDecimal("100.00"))
                .targetAmount(new BigDecimal("400000.00"))
                .rate(new BigDecimal("4000.00")).build());

        ConvertCurrencyRequest request = ConvertCurrencyRequest.builder()
                .sourceCurrency("USD").targetCurrency("COP")
                .amount(new BigDecimal("100.00")).build();

        mockMvc.perform(post("/api/v1/fx/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(400000.00));
    }

    @Test
    void quote_shouldReturnQuote() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(fxService.getRate(any(), any())).thenReturn(null);

        mockMvc.perform(post("/api/v1/fx/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"ACC-000001\",\"destinationAccountId\":\"ACC-000002\","
                                + "\"amount\":100.00,\"sourceCurrency\":\"USD\",\"destinationCurrency\":\"COP\"}"))
                .andExpect(status().isOk());
    }
}

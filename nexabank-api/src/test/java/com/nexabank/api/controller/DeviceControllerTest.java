package com.nexabank.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.firebase.auth.FirebaseAuth;
import com.nexabank.api.dto.device.DeviceResponse;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.DeviceService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

// addFilters = false: estos tests ejercitan solo la capa web (controller + DTOs),
// no la cadena de filtros de Spring Security (ya cubierta por tests de SecurityConfig).
@WebMvcTest(DeviceController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceService deviceService;

    // Requerido para que el contexto cargue: SecurityConfig (cargado por
    // SecurityAutoConfiguration en el slice @WebMvcTest) instancia FirebaseAuthenticationFilter,
    // que depende de este bean.
    @MockBean
    private FirebaseAuth firebaseAuth;

    @MockBean
    private SecurityUtils securityUtils;

    @Test
    void getDevices_shouldReturnList() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(deviceService.getDevicesByUser("USR-000001")).thenReturn(
                List.of(DeviceResponse.builder().id("DEV-00000001").deviceName("iPhone 15").trusted(true).build()));

        mockMvc.perform(get("/api/v1/me/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("DEV-00000001"));
    }

    @Test
    void revokeDevice_shouldReturnNoContent() throws Exception {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");

        mockMvc.perform(delete("/api/v1/me/devices/DEV-00000001"))
                .andExpect(status().isNoContent());
    }
}

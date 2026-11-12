package com.nexabank.api.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.nexabank.api.config.CorsConfig;
import com.nexabank.api.controller.AccountController;
import com.nexabank.api.controller.AdminController;
import com.nexabank.api.controller.AdminKycController;
import com.nexabank.api.controller.QrController;
import com.nexabank.api.dto.account.AccountResponse;
import com.nexabank.api.service.AccountService;
import com.nexabank.api.service.AdminService;
import com.nexabank.api.service.ConfirmationTokenService;
import com.nexabank.api.service.KycService;
import com.nexabank.api.service.QrService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica, con la cadena de filtros de Spring Security REAL (sin
 * {@code addFilters = false}), que tras el cambio de {@code anyRequest().permitAll()} a
 * {@code anyRequest().authenticated()} (mejora #12):
 * <ul>
 *   <li>Un endpoint privado sin token responde 401.</li>
 *   <li>Un endpoint privado con token Firebase válido responde con éxito (no 401/403).</li>
 *   <li>Un endpoint explícitamente público ({@code qr/decode}) responde sin token.</li>
 *   <li>Un endpoint ADMIN con token válido de usuario normal (sin rol ADMIN) responde 403
 *       ({@code @PreAuthorize}, mejora #20).</li>
 * </ul>
 */
@WebMvcTest(controllers = {AccountController.class, QrController.class, AdminController.class, AdminKycController.class})
@Import({SecurityConfig.class, FirebaseAuthenticationFilter.class, RateLimitFilter.class, CorsConfig.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FirebaseAuth firebaseAuth;

    @MockBean
    private AccountService accountService;

    @MockBean
    private QrService qrService;

    @MockBean
    private AdminService adminService;

    @MockBean
    private KycService kycService;

    // SecurityUtils es un @Component plano excluido por el TypeExcludeFilter de @WebMvcTest;
    // se mockea aquí porque este test ejercita la capa de Spring Security (filtros +
    // @PreAuthorize), no la verificación manual de SecurityUtils.
    @MockBean
    private SecurityUtils securityUtils;

    @MockBean
    private ConfirmationTokenService confirmationTokenService;

    @Test
    void privateEndpoint_withoutToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void privateEndpoint_withValidToken_shouldPassSecurityAndReachController() throws Exception {
        FirebaseToken token = mockToken("uid-123", List.of("USER"));
        when(firebaseAuth.verifyIdToken("valid-token")).thenReturn(token);
        when(securityUtils.getCurrentUserId()).thenReturn("uid-123");
        when(accountService.getAccountsByUser("uid-123")).thenReturn(List.<AccountResponse>of());

        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());
    }

    @Test
    void privateEndpoint_withInvalidToken_shouldReturnUnauthorized() throws Exception {
        when(firebaseAuth.verifyIdToken("bad-token"))
                .thenThrow(mock(FirebaseAuthException.class));

        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpoint_qrDecode_shouldBeAccessibleWithoutToken() throws Exception {
        when(qrService.decodeQr("nexabank:pay:ACC-000001:Merchant"))
                .thenReturn(Map.of("accountId", "ACC-000001", "merchant", "Merchant"));

        mockMvc.perform(get("/api/v1/qr/decode").param("data", "nexabank:pay:ACC-000001:Merchant"))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoint_withValidTokenButNoAdminRole_shouldReturnForbidden() throws Exception {
        FirebaseToken token = mockToken("uid-123", List.of("USER"));
        when(firebaseAuth.verifyIdToken("valid-token")).thenReturn(token);

        mockMvc.perform(get("/api/v1/admin/metrics").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_withAdminRole_shouldSucceed() throws Exception {
        FirebaseToken token = mockToken("uid-admin", List.of("ADMIN"));
        when(firebaseAuth.verifyIdToken("admin-token")).thenReturn(token);
        when(adminService.getMetrics()).thenReturn(Map.of("totalUsers", 0));

        mockMvc.perform(get("/api/v1/admin/metrics").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoint_withoutToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminKycEndpoint_withValidTokenButNoAdminRole_shouldReturnForbidden() throws Exception {
        FirebaseToken token = mockToken("uid-123", List.of("USER"));
        when(firebaseAuth.verifyIdToken("valid-token")).thenReturn(token);

        mockMvc.perform(get("/api/v1/admin/kyc/pending").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminKycEndpoint_withAdminRole_shouldSucceed() throws Exception {
        FirebaseToken token = mockToken("uid-admin", List.of("ADMIN"));
        when(firebaseAuth.verifyIdToken("admin-token")).thenReturn(token);
        when(kycService.getPending()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/kyc/pending").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    private FirebaseToken mockToken(String uid, List<String> roles) {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getEmail()).thenReturn(uid + "@example.com");
        when(token.getClaims()).thenReturn(Map.of("roles", roles));
        return token;
    }
}

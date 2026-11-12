package com.nexabank.api.controller;

import com.nexabank.api.dto.auth.AuthResponse;
import com.nexabank.api.exception.AuthenticationRequiredException;
import com.nexabank.api.dto.auth.PhoneStartRequest;
import com.nexabank.api.dto.auth.PhoneVerifyRequest;
import com.nexabank.api.dto.auth.PinSetupRequest;
import com.nexabank.api.dto.auth.PinVerifyRequest;
import com.nexabank.api.dto.user.RegisterRequest;
import com.nexabank.api.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.Map;

/**
 * Controlador de autenticación según FlujoAuth.md.
 *
 * <p>Rutas:
 * <ul>
 *   <li>POST /api/v1/auth/phone/start</li>
 *   <li>POST /api/v1/auth/phone/verify</li>
 *   <li>POST /api/v1/auth/register</li>
 *   <li>POST /api/v1/auth/pin/setup</li>
 *   <li>POST /api/v1/auth/pin/verify</li>
 *   <li>GET /api/v1/auth/me</li>
 *   <li>POST /api/v1/auth/logout</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Inicia la verificación telefónica (OTP).
     */
    @PostMapping("/phone/start")
    public ResponseEntity<AuthResponse> startPhoneVerification(@Valid @RequestBody PhoneStartRequest request) {
        return ResponseEntity.ok(authService.startPhoneVerification(request.getPhone()));
    }

    /**
     * Valida el OTP enviado por SMS.
     */
    @PostMapping("/phone/verify")
    public ResponseEntity<AuthResponse> verifyPhone(@Valid @RequestBody PhoneVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyPhone(request.getPhone(), request.getCode()));
    }

    /**
     * Completa el perfil del usuario nuevo y crea la cuenta de demo.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /**
     * Configura el PIN del usuario.
     */
    @PostMapping("/pin/setup")
    public ResponseEntity<AuthResponse> setupPin(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                  @Valid @RequestBody PinSetupRequest request) {
        String uid = extractUid(authHeader);
        return ResponseEntity.ok(authService.setupPin(uid, request.getPin(),
                request.getDeviceId(), request.getDeviceName(), request.getPlatform()));
    }

    /**
     * Valida el PIN del usuario (para desbloqueo de sesión).
     */
    @PostMapping("/pin/verify")
    public ResponseEntity<AuthResponse> verifyPin(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                   @Valid @RequestBody PinVerifyRequest request) {
        String uid = extractUid(authHeader);
        return ResponseEntity.ok(authService.verifyPin(uid, request.getPin(),
                request.getDeviceId(), request.getDeviceName(), request.getPlatform()));
    }

    /**
     * Obtiene el perfil del usuario autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String uid = extractUid(authHeader);
        return ResponseEntity.ok(authService.getCurrentUser(uid));
    }

    /**
     * Verifica si un usuario existe por número de teléfono.
     */
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Object>> userExists(@RequestParam("phone") String phone) {
        boolean exists = authService.userExistsByPhone(phone);
        return ResponseEntity.ok(Map.of("exists", exists, "phone", phone));
    }

    /**
     * Cierra la sesión en el servidor.
     */
    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout() {
        return ResponseEntity.ok(authService.logout());
    }

    private String extractUid(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AuthenticationRequiredException("Missing or invalid Authorization header. Provide a valid Firebase ID token.");
        }
        return authService.extractUidFromToken(authHeader.replace("Bearer ", ""));
    }
}
package com.nexabank.api.controller;

import com.nexabank.api.dto.user.UserLookupResponse;
import com.nexabank.api.dto.user.UserResponse;
import com.nexabank.api.dto.user.UserUpdateRequest;
import com.nexabank.api.service.UserService;
import com.nexabank.api.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final UserService userService;
    private final SecurityUtils securityUtils;

    public UserController(UserService userService, SecurityUtils securityUtils) {
        this.userService = userService;
        this.securityUtils = securityUtils;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        String userId = securityUtils.getCurrentUserId();
        UserResponse user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(@Valid @RequestBody UserUpdateRequest request) {
        String userId = securityUtils.getCurrentUserId();
        UserResponse updated = userService.updateUser(userId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Resuelve el destinatario de una transferencia por teléfono. Requiere
     * auth (a diferencia de /auth/exists, que es pública y ya está marcada
     * como gap de enumeración en docs/API_AUDIT.md) y está en la lista de
     * rutas protegidas por RateLimitFilter — buscar por teléfono es
     * exactamente el tipo de endpoint que se presta a enumeración masiva si
     * no se limita.
     */
    @GetMapping("/lookup")
    public ResponseEntity<UserLookupResponse> lookupByPhone(
            @RequestParam("phone")
            @NotBlank(message = "Phone is required")
            @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone must be a valid mobile number in E.164 format")
            String phone) {
        String userId = securityUtils.getCurrentUserId();
        UserLookupResponse result = userService.lookupByPhone(phone, userId);
        return ResponseEntity.ok(result);
    }
}

package com.nexabank.api.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de request para registro completo de usuario (flujo B del FlujoAuth.md).
 *
 * <p>Campos que el frontend envía después de verificar el OTP:
 * <pre>
 * { phone, firstName, lastName, documentId, address, country, email, displayName, pin }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone must be a valid mobile number in E.164 format")
    private String phone;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must be at most 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must be at most 100 characters")
    private String lastName;

    @NotBlank(message = "Document ID is required")
    @Size(max = 50, message = "Document ID must be at most 50 characters")
    private String documentId;

    @NotBlank(message = "Address is required")
    @Size(max = 200, message = "Address must be at most 200 characters")
    private String address;

    @NotBlank(message = "Country is required")
    @Size(max = 3, message = "Country must be a valid ISO code (e.g. CO)")
    private String country;

    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name must be at most 100 characters")
    private String displayName;

    @NotBlank(message = "PIN is required")
    @Size(min = 4, max = 6, message = "PIN must be 4-6 digits")
    private String pin;
}

package com.nexabank.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para POST /api/v1/auth/phone/start.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneStartRequest {

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone must be a valid mobile number in E.164 format")
    private String phone;
}
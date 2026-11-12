package com.nexabank.api.dto.user;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de request para PUT /api/v1/users/me.
 *
 * <p>Campos que NO se pueden modificar desde este endpoint:
 * id, status, roles, createdAt, security metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @Size(max = 100, message = "Display name must be at most 100 characters")
    private String displayName;

    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String phone;
}

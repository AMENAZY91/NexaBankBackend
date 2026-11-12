package com.nexabank.api.mapper;

import com.nexabank.api.dto.user.UserResponse;
import com.nexabank.api.dto.user.UserUpdateRequest;
import com.nexabank.api.model.User;
import org.springframework.stereotype.Component;

/**
 * Mapper para transformaciones User ↔ DTO.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .phone(user.getPhone())
                .status(user.getStatus())
                .roles(user.getRoles())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    public void updateFromRequest(UserUpdateRequest request, User user) {
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
    }
}

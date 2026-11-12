package com.nexabank.api.mapper;

import com.nexabank.api.dto.device.DeviceResponse;
import com.nexabank.api.model.Device;
import org.springframework.stereotype.Component;

/**
 * Mapper para transformaciones Device → DTO.
 */
@Component
public class DeviceMapper {

    public DeviceResponse toResponse(Device device) {
        return DeviceResponse.builder()
                .id(device.getId())
                .deviceName(device.getDeviceName())
                .platform(device.getPlatform())
                .trusted(device.isTrusted())
                .lastSeenAt(device.getLastSeenAt())
                .createdAt(device.getCreatedAt())
                .build();
    }
}

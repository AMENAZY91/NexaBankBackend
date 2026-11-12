package com.nexabank.api.dto.device;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO de respuesta para dispositivos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {

    private String id;
    private String deviceName;
    private String platform;
    private boolean trusted;
    private Instant lastSeenAt;
    private Instant createdAt;
}

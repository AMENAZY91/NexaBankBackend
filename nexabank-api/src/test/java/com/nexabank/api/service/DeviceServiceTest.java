package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.DeviceMapper;
import com.nexabank.api.model.Device;
import com.nexabank.api.repository.DeviceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(deviceRepository, new DeviceMapper());
    }

    @Test
    void recordSeen_shouldCreateNewDevice_whenNotSeenBefore() {
        when(deviceRepository.findByUserIdAndDeviceId("USR-000001", "device-abc"))
                .thenReturn(Optional.empty());
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        deviceService.recordSeen("USR-000001", "device-abc", "iPhone 15", "ios");

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        Device saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo("USR-000001");
        assertThat(saved.getDeviceId()).isEqualTo("device-abc");
        assertThat(saved.getDeviceName()).isEqualTo("iPhone 15");
        assertThat(saved.getPlatform()).isEqualTo("ios");
        assertThat(saved.isTrusted()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void recordSeen_shouldUpdateExistingDevice_preservingIdAndCreatedAt() {
        Instant originalCreatedAt = Instant.parse("2026-01-01T00:00:00Z");
        Device existing = Device.builder()
                .id("DEV-00000001").userId("USR-000001").deviceId("device-abc")
                .deviceName("Old name").platform("android").trusted(false)
                .createdAt(originalCreatedAt).lastSeenAt(originalCreatedAt).build();
        when(deviceRepository.findByUserIdAndDeviceId("USR-000001", "device-abc"))
                .thenReturn(Optional.of(existing));
        when(deviceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        deviceService.recordSeen("USR-000001", "device-abc", "New name", "android");

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        Device saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo("DEV-00000001");
        assertThat(saved.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(saved.getDeviceName()).isEqualTo("New name");
        assertThat(saved.isTrusted()).isTrue();
        assertThat(saved.getLastSeenAt()).isAfter(originalCreatedAt);
    }

    @Test
    void recordSeen_shouldBeNoOp_whenDeviceIdBlank() {
        deviceService.recordSeen("USR-000001", "  ", "iPhone 15", "ios");
        deviceService.recordSeen("USR-000001", null, "iPhone 15", "ios");

        verify(deviceRepository, never()).save(any());
        verify(deviceRepository, never()).findByUserIdAndDeviceId(any(), any());
    }

    @Test
    void getDevicesByUser_shouldReturnList() {
        Device device = Device.builder().id("DEV-00000001").userId("USR-000001")
                .deviceName("iPhone 15").trusted(true).build();
        when(deviceRepository.findByUserId("USR-000001")).thenReturn(List.of(device));

        List<com.nexabank.api.dto.device.DeviceResponse> result =
                deviceService.getDevicesByUser("USR-000001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("DEV-00000001");
    }

    @Test
    void revokeDevice_shouldSucceed_whenOwner() {
        Device device = Device.builder().id("DEV-00000001").userId("USR-000001").build();
        when(deviceRepository.findById("DEV-00000001")).thenReturn(Optional.of(device));

        deviceService.revokeDevice("DEV-00000001", "USR-000001");

        verify(deviceRepository).revokeTrust("DEV-00000001");
    }

    @Test
    void revokeDevice_shouldThrowNotFound_whenMissing() {
        when(deviceRepository.findById("DEV-999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.revokeDevice("DEV-999999", "USR-000001"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void revokeDevice_shouldThrowUnauthorized_whenNotOwner() {
        Device device = Device.builder().id("DEV-00000001").userId("USR-000001").build();
        when(deviceRepository.findById("DEV-00000001")).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> deviceService.revokeDevice("DEV-00000001", "USR-000002"))
                .isInstanceOf(UnauthorizedOperationException.class);
    }
}

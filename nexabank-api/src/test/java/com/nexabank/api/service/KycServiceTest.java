package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexabank.api.dto.kyc.KycProfileResponse;
import com.nexabank.api.dto.kyc.SubmitKycRequest;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.mapper.KycMapper;
import com.nexabank.api.model.KycProfile;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.KycProfileRepository;
import com.nexabank.api.service.provider.KycProvider;
import com.nexabank.api.service.provider.KycStatus;
import com.nexabank.api.service.provider.KycSubmissionResult;
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
class KycServiceTest {

    @Mock
    private KycProfileRepository kycProfileRepository;
    @Mock
    private KycProvider kycProvider;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogRepository auditLogRepository;

    private KycService kycService;

    @BeforeEach
    void setUp() {
        kycService = new KycService(kycProfileRepository, kycProvider, notificationService,
                auditLogRepository, new KycMapper());
    }

    private SubmitKycRequest validRequest() {
        return SubmitKycRequest.builder()
                .documentType("NATIONAL_ID").documentNumber("12345678")
                .fullName("Juan Perez").dateOfBirth("1990-01-01").address("Calle 123").build();
    }

    @Test
    void getMyProfile_shouldReturnNotStarted_whenNoneExists() {
        when(kycProfileRepository.findByUserId("USR-000001")).thenReturn(Optional.empty());

        KycProfileResponse response = kycService.getMyProfile("USR-000001");

        assertThat(response.getStatus()).isEqualTo("NOT_STARTED");
    }

    @Test
    void submit_shouldPersistUnderReview_andMaskDocumentNumberInAudit() {
        when(kycProfileRepository.findByUserId("USR-000001")).thenReturn(Optional.empty());
        when(kycProvider.submit(any())).thenReturn(new KycSubmissionResult("VER-ABC123", KycStatus.UNDER_REVIEW));
        when(kycProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KycProfileResponse response = kycService.submit(validRequest(), "USR-000001");

        assertThat(response.getStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(response.getDocumentNumber()).isEqualTo("12345678"); // completo para el propio dueño

        ArgumentCaptor<com.nexabank.api.model.AuditLog> auditCaptor =
                ArgumentCaptor.forClass(com.nexabank.api.model.AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        Object maskedNumber = auditCaptor.getValue().getMetadata().get("documentNumber");
        assertThat(maskedNumber).isEqualTo("****5678");
    }

    @Test
    void submit_shouldThrow_whenAlreadyApproved() {
        when(kycProfileRepository.findByUserId("USR-000001"))
                .thenReturn(Optional.of(KycProfile.builder().userId("USR-000001").status("APPROVED").build()));

        assertThatThrownBy(() -> kycService.submit(validRequest(), "USR-000001"))
                .isInstanceOf(InvalidTransactionException.class);
        verify(kycProvider, never()).submit(any());
    }

    @Test
    void submit_shouldAllowRetry_afterRejection() {
        when(kycProfileRepository.findByUserId("USR-000001"))
                .thenReturn(Optional.of(KycProfile.builder().userId("USR-000001").status("REJECTED").build()));
        when(kycProvider.submit(any())).thenReturn(new KycSubmissionResult("VER-XYZ789", KycStatus.UNDER_REVIEW));
        when(kycProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KycProfileResponse response = kycService.submit(validRequest(), "USR-000001");

        assertThat(response.getStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void approve_shouldSucceed_whenUnderReview() {
        when(kycProfileRepository.findByUserId("USR-000001")).thenReturn(Optional.of(
                KycProfile.builder().userId("USR-000001").status("UNDER_REVIEW").documentNumber("12345678").build()));
        when(kycProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KycProfileResponse response = kycService.approve("USR-000001", "ADMIN-001");

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        verify(notificationService).notify(eq("USR-000001"), eq("KYC_APPROVED"), any(), any(), any(), any());
    }

    @Test
    void approve_shouldThrowNotFound_whenNoProfile() {
        when(kycProfileRepository.findByUserId("USR-999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kycService.approve("USR-999999", "ADMIN-001"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void approve_shouldThrow_whenStatusNotReviewable() {
        when(kycProfileRepository.findByUserId("USR-000001"))
                .thenReturn(Optional.of(KycProfile.builder().userId("USR-000001").status("NOT_STARTED").build()));

        assertThatThrownBy(() -> kycService.approve("USR-000001", "ADMIN-001"))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void approve_shouldThrow_whenAlreadyApproved() {
        when(kycProfileRepository.findByUserId("USR-000001"))
                .thenReturn(Optional.of(KycProfile.builder().userId("USR-000001").status("APPROVED").build()));

        assertThatThrownBy(() -> kycService.approve("USR-000001", "ADMIN-001"))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void reject_shouldSucceed_withReason() {
        when(kycProfileRepository.findByUserId("USR-000001")).thenReturn(Optional.of(
                KycProfile.builder().userId("USR-000001").status("SUBMITTED").documentNumber("12345678").build()));
        when(kycProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KycProfileResponse response = kycService.reject("USR-000001", "ADMIN-001", "Documento ilegible");

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getRejectionReason()).isEqualTo("Documento ilegible");
        verify(notificationService).notify(eq("USR-000001"), eq("KYC_REJECTED"), any(), any(), any(), any());
    }

    @Test
    void getPending_shouldReturnMappedList() {
        when(kycProfileRepository.findPending()).thenReturn(
                List.of(KycProfile.builder().userId("USR-000001").status("SUBMITTED").build()));

        List<KycProfileResponse> pending = kycService.getPending();

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getUserId()).isEqualTo("USR-000001");
    }

    @Test
    void resolveFromWebhook_shouldApprove_whenVerificationIdMatches() {
        when(kycProfileRepository.findByUserId("USR-000001")).thenReturn(Optional.of(
                KycProfile.builder().userId("USR-000001").status("UNDER_REVIEW")
                        .verificationId("VER-ABC123").documentNumber("12345678").build()));
        when(kycProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        kycService.resolveFromWebhook("USR-000001", "VER-ABC123", "APPROVED", null);

        verify(notificationService).notify(eq("USR-000001"), eq("KYC_APPROVED"), any(), any(), any(), any());
    }

    @Test
    void resolveFromWebhook_shouldThrow_whenVerificationIdDoesNotMatch() {
        when(kycProfileRepository.findByUserId("USR-000001")).thenReturn(Optional.of(
                KycProfile.builder().userId("USR-000001").status("UNDER_REVIEW")
                        .verificationId("VER-ABC123").build()));

        assertThatThrownBy(() -> kycService.resolveFromWebhook("USR-000001", "VER-DIFFERENT", "APPROVED", null))
                .isInstanceOf(InvalidTransactionException.class);
        verify(kycProfileRepository, never()).save(any());
    }

    @Test
    void maskDocumentNumber_shouldKeepOnlyLastFourDigits() {
        assertThat(KycService.maskDocumentNumber("12345678")).isEqualTo("****5678");
        assertThat(KycService.maskDocumentNumber("123")).isEqualTo("****");
        assertThat(KycService.maskDocumentNumber(null)).isEqualTo("****");
    }
}

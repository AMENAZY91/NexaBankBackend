package com.nexabank.api.service;

import com.nexabank.api.dto.kyc.KycProfileResponse;
import com.nexabank.api.dto.kyc.SubmitKycRequest;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.mapper.KycMapper;
import com.nexabank.api.model.AuditLog;
import com.nexabank.api.model.KycProfile;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.KycProfileRepository;
import com.nexabank.api.service.provider.KycProvider;
import com.nexabank.api.service.provider.KycSubmission;
import com.nexabank.api.service.provider.KycSubmissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Servicio de verificación de identidad (KYC).
 *
 * <p>Compone sobre {@link KycProvider} (mock hoy) para la verificación en sí — este
 * servicio solo orquesta el ciclo de vida del {@link KycProfile} y la autorización de
 * quién puede resolverlo (admin vía {@link #approve}/{@link #reject}, o un proveedor
 * real futuro vía {@link #resolveFromWebhook}).
 *
 * <p>{@code documentNumber} nunca se loguea ni se guarda en claro en
 * {@code AuditLog.metadata} — ver {@link #maskDocumentNumber}. Sí se devuelve completo
 * en {@link KycProfileResponse} porque ese es un acceso legítimo del propio dato por su
 * dueño (o por un admin en revisión), no un log/auditoría de acceso general.
 */
@Service
public class KycService {

    private static final Logger log = LoggerFactory.getLogger(KycService.class);

    private final KycProfileRepository kycProfileRepository;
    private final KycProvider kycProvider;
    private final NotificationService notificationService;
    private final AuditLogRepository auditLogRepository;
    private final KycMapper kycMapper;

    public KycService(KycProfileRepository kycProfileRepository,
                       KycProvider kycProvider,
                       NotificationService notificationService,
                       AuditLogRepository auditLogRepository,
                       KycMapper kycMapper) {
        this.kycProfileRepository = kycProfileRepository;
        this.kycProvider = kycProvider;
        this.notificationService = notificationService;
        this.auditLogRepository = auditLogRepository;
        this.kycMapper = kycMapper;
    }

    public KycProfileResponse getMyProfile(String userId) {
        KycProfile profile = kycProfileRepository.findByUserId(userId)
                .orElseGet(() -> KycProfile.builder().userId(userId).status("NOT_STARTED").build());
        return kycMapper.toResponse(profile);
    }

    public KycProfileResponse submit(SubmitKycRequest request, String userId) {
        KycProfile existing = kycProfileRepository.findByUserId(userId).orElse(null);
        if (existing != null && "APPROVED".equalsIgnoreCase(existing.getStatus())) {
            throw new InvalidTransactionException("KYC is already approved for this user");
        }

        KycSubmissionResult result = kycProvider.submit(new KycSubmission(
                userId, request.getDocumentType(), request.getDocumentNumber(),
                request.getFullName(), request.getDateOfBirth(), request.getAddress()));

        Instant now = Instant.now();
        KycProfile profile = KycProfile.builder()
                .userId(userId)
                .status(result.status().name())
                .verificationId(result.verificationId())
                .documentType(request.getDocumentType())
                .documentNumber(request.getDocumentNumber())
                .fullName(request.getFullName())
                .dateOfBirth(request.getDateOfBirth())
                .address(request.getAddress())
                .submittedAt(now)
                .build();
        KycProfile saved = kycProfileRepository.save(profile);

        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action("KYC_SUBMITTED")
                .resourceType("KYC_PROFILE")
                .resourceId(userId)
                .status("SUCCESS")
                .timestamp(now)
                .metadata(Map.of(
                        "documentType", request.getDocumentType(),
                        "documentNumber", maskDocumentNumber(request.getDocumentNumber())
                ))
                .build());

        log.info("KYC submitted for user {} (verificationId={}, status={})",
                userId, result.verificationId(), result.status());

        return kycMapper.toResponse(saved);
    }

    public List<KycProfileResponse> getPending() {
        return kycProfileRepository.findPending().stream().map(kycMapper::toResponse).toList();
    }

    public KycProfileResponse approve(String userId, String adminId) {
        KycProfile profile = getReviewableProfile(userId);

        KycProfile approved = profile.toBuilder()
                .status("APPROVED")
                .reviewedAt(Instant.now())
                .reviewerId(adminId)
                .build();
        KycProfile saved = kycProfileRepository.save(approved);

        notifyAndAudit(saved, adminId, "KYC_APPROVED", "KYC aprobado",
                "Tu verificación de identidad fue aprobada");

        return kycMapper.toResponse(saved);
    }

    public KycProfileResponse reject(String userId, String adminId, String rejectionReason) {
        KycProfile profile = getReviewableProfile(userId);

        KycProfile rejected = profile.toBuilder()
                .status("REJECTED")
                .reviewedAt(Instant.now())
                .reviewerId(adminId)
                .rejectionReason(rejectionReason)
                .build();
        KycProfile saved = kycProfileRepository.save(rejected);

        notifyAndAudit(saved, adminId, "KYC_REJECTED", "KYC rechazado",
                "Tu verificación de identidad fue rechazada: " + rejectionReason);

        return kycMapper.toResponse(saved);
    }

    /**
     * Resolución asíncrona vía webhook de un proveedor real futuro — sin tráfico real
     * hoy porque {@code NoOpWebhookSignatureVerifier} rechaza toda llamada antes de
     * llegar aquí (ver {@code KycWebhookController}). Valida que el {@code verificationId}
     * coincida con el emitido en {@link #submit} para no aplicar una resolución vieja
     * sobre una verificación distinta (ej. tras un reenvío/replay del proveedor).
     */
    public void resolveFromWebhook(String userId, String verificationId, String status, String rejectionReason) {
        KycProfile profile = kycProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC profile not found for user: " + userId));

        if (!verificationId.equals(profile.getVerificationId())) {
            throw new InvalidTransactionException("verificationId does not match the active KYC submission");
        }

        KycProfile resolved = profile.toBuilder()
                .status(status)
                .reviewedAt(Instant.now())
                .reviewerId("SYSTEM_WEBHOOK")
                .rejectionReason(rejectionReason)
                .build();
        KycProfile saved = kycProfileRepository.save(resolved);

        if ("APPROVED".equalsIgnoreCase(status)) {
            notifyAndAudit(saved, "SYSTEM_WEBHOOK", "KYC_APPROVED", "KYC aprobado",
                    "Tu verificación de identidad fue aprobada");
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            notifyAndAudit(saved, "SYSTEM_WEBHOOK", "KYC_REJECTED", "KYC rechazado",
                    "Tu verificación de identidad fue rechazada" + (rejectionReason != null ? ": " + rejectionReason : ""));
        }
    }

    private KycProfile getReviewableProfile(String userId) {
        KycProfile profile = kycProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC profile not found for user: " + userId));

        if (!"SUBMITTED".equalsIgnoreCase(profile.getStatus()) && !"UNDER_REVIEW".equalsIgnoreCase(profile.getStatus())) {
            throw new InvalidTransactionException(
                    "Only SUBMITTED or UNDER_REVIEW profiles can be reviewed (current status: " + profile.getStatus() + ")");
        }
        return profile;
    }

    private void notifyAndAudit(KycProfile profile, String reviewerId, String notificationType,
                                 String title, String body) {
        notificationService.notify(profile.getUserId(), notificationType, title, body,
                "KYC_PROFILE", profile.getUserId());

        auditLogRepository.save(AuditLog.builder()
                .userId(reviewerId)
                .action(notificationType)
                .resourceType("KYC_PROFILE")
                .resourceId(profile.getUserId())
                .status("SUCCESS")
                .timestamp(Instant.now())
                .metadata(Map.of(
                        "documentNumber", maskDocumentNumber(profile.getDocumentNumber()),
                        "reviewedUserId", profile.getUserId()
                ))
                .build());
    }

    /**
     * Enmascara todo salvo los últimos 4 caracteres — nunca se loguea ni se audita un
     * número de documento completo (mismo criterio que el audit aplicó al PIN de usuario).
     */
    static String maskDocumentNumber(String raw) {
        if (raw == null || raw.length() <= 4) {
            return "****";
        }
        return "*".repeat(raw.length() - 4) + raw.substring(raw.length() - 4);
    }
}

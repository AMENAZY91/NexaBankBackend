package com.nexabank.api.service;

import com.nexabank.api.dto.topup.CreateTopupRequest;
import com.nexabank.api.dto.topup.TopupResponse;
import com.nexabank.api.exception.InvalidCurrencyException;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.TopupMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.AuditLog;
import com.nexabank.api.model.IdempotencyKey;
import com.nexabank.api.model.Topup;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.IdempotencyRepository;
import com.nexabank.api.repository.TopupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio de recargas (topups).
 *
 * <p>Mismo nivel de cuidado que {@code TransferService}: idempotencia vía header
 * {@code Idempotency-Key}, mutación de balance atómica vía
 * {@code AccountRepository.applyBalanceDelta}, auditoría y notificación tras éxito.
 * {@code source} es mock (CARD/BANK_TRANSFER/CASH) — no hay integración real con un
 * proveedor de pagos en esta fase.
 */
@Service
public class TopupService {

    private static final Logger log = LoggerFactory.getLogger(TopupService.class);

    private final TopupRepository topupRepository;
    private final AccountRepository accountRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final AuditLogRepository auditLogRepository;
    private final TopupMapper topupMapper;
    private final NotificationService notificationService;

    public TopupService(TopupRepository topupRepository,
                         AccountRepository accountRepository,
                         IdempotencyRepository idempotencyRepository,
                         AuditLogRepository auditLogRepository,
                         TopupMapper topupMapper,
                         NotificationService notificationService) {
        this.topupRepository = topupRepository;
        this.accountRepository = accountRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.auditLogRepository = auditLogRepository;
        this.topupMapper = topupMapper;
        this.notificationService = notificationService;
    }

    public List<TopupResponse> getTopupsByUser(String userId) {
        return topupRepository.findByUserId(userId).stream()
                .map(topupMapper::toResponse)
                .toList();
    }

    public TopupResponse getTopupById(String id, String userId) {
        Topup topup = getOwnedTopup(id, userId);
        return topupMapper.toResponse(topup);
    }

    public TopupResponse createTopup(CreateTopupRequest request, String idempotencyKeyHeader, String userId) {
        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            Optional<IdempotencyKey> existingKey = idempotencyRepository.findByKey(idempotencyKeyHeader);
            if (existingKey.isPresent()) {
                IdempotencyKey ik = existingKey.get();
                if ("COMPLETED".equals(ik.getStatus()) && ik.getResponseReference() != null) {
                    log.info("Idempotent request detected for key {}. Returning existing topup {}",
                            idempotencyKeyHeader, ik.getResponseReference());
                    return getTopupById(ik.getResponseReference(), userId);
                }
            }
        }

        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.getAccountId()));

        if (!account.getUserId().equals(userId)) {
            throw new UnauthorizedOperationException("The account does not belong to the authenticated user");
        }
        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new InvalidTransactionException("Account is not active");
        }
        if (!account.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw new InvalidCurrencyException(
                    "Account currency (" + account.getCurrency() + ") does not match request currency ("
                            + request.getCurrency() + ")");
        }

        Instant now = Instant.now();
        Topup pending = Topup.builder()
                .userId(userId)
                .accountId(account.getId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .source(request.getSource())
                .status("PENDING")
                .idempotencyKey(idempotencyKeyHeader)
                .createdAt(now)
                .build();
        Topup savedPending = topupRepository.save(pending);

        try {
            accountRepository.applyBalanceDelta(account.getId(), request.getAmount());
        } catch (RuntimeException e) {
            Topup failed = savedPending.toBuilder().status("FAILED").completedAt(Instant.now()).build();
            topupRepository.save(failed);
            auditLogRepository.save(AuditLog.builder()
                    .userId(userId)
                    .action("TOPUP_FAILED")
                    .resourceType("TOPUP")
                    .resourceId(failed.getId())
                    .status("FAILED")
                    .timestamp(Instant.now())
                    .metadata(Map.of("accountId", account.getId(), "amount", request.getAmount().doubleValue(),
                            "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()))
                    .build());
            throw e;
        }

        Topup completed = savedPending.toBuilder().status("COMPLETED").completedAt(now).build();
        Topup saved = topupRepository.save(completed);

        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            idempotencyRepository.save(IdempotencyKey.builder()
                    .key(idempotencyKeyHeader)
                    .userId(userId)
                    .operation("TOPUP")
                    .status("COMPLETED")
                    .responseReference(saved.getId())
                    .createdAt(now)
                    .expiresAt(now.plus(24, ChronoUnit.HOURS))
                    .build());
        }

        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action("TOPUP")
                .resourceType("TOPUP")
                .resourceId(saved.getId())
                .status("SUCCESS")
                .timestamp(now)
                .metadata(Map.of("accountId", account.getId(), "amount", request.getAmount().doubleValue(),
                        "source", request.getSource()))
                .build());

        notificationService.notify(userId, "TOPUP_COMPLETED", "Recarga completada",
                "Recargaste " + request.getAmount() + " " + request.getCurrency() + " a tu cuenta",
                "TOPUP", saved.getId());

        log.info("Topup {} completed: {} {} to account {}", saved.getId(), request.getAmount(),
                request.getCurrency(), account.getId());

        return topupMapper.toResponse(saved);
    }

    private Topup getOwnedTopup(String id, String userId) {
        Topup topup = topupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Topup not found with ID: " + id));

        if (userId != null && !userId.equals(topup.getUserId())) {
            throw new ResourceNotFoundException("Topup not found with ID: " + id);
        }

        return topup;
    }
}

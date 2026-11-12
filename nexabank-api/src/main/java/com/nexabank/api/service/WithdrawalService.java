package com.nexabank.api.service;

import com.nexabank.api.dto.withdrawal.CreateWithdrawalRequest;
import com.nexabank.api.dto.withdrawal.WithdrawalResponse;
import com.nexabank.api.exception.InsufficientFundsException;
import com.nexabank.api.exception.InvalidCurrencyException;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.WithdrawalMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.AuditLog;
import com.nexabank.api.model.IdempotencyKey;
import com.nexabank.api.model.Withdrawal;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.IdempotencyRepository;
import com.nexabank.api.repository.WithdrawalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio de retiros (withdrawals).
 *
 * <p>Mismo nivel de cuidado que {@code TransferService}: idempotencia vía header
 * {@code Idempotency-Key}, mutación de balance atómica vía
 * {@code AccountRepository.applyBalanceDelta} (que revalida fondos suficientes dentro
 * de la propia transacción Firestore), auditoría y notificación tras éxito.
 * {@code destination} es mock (CARD/BANK_TRANSFER/CASH) — no hay integración real con
 * un proveedor de pagos en esta fase.
 */
@Service
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    private final WithdrawalRepository withdrawalRepository;
    private final AccountRepository accountRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final AuditLogRepository auditLogRepository;
    private final WithdrawalMapper withdrawalMapper;
    private final NotificationService notificationService;

    public WithdrawalService(WithdrawalRepository withdrawalRepository,
                              AccountRepository accountRepository,
                              IdempotencyRepository idempotencyRepository,
                              AuditLogRepository auditLogRepository,
                              WithdrawalMapper withdrawalMapper,
                              NotificationService notificationService) {
        this.withdrawalRepository = withdrawalRepository;
        this.accountRepository = accountRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.auditLogRepository = auditLogRepository;
        this.withdrawalMapper = withdrawalMapper;
        this.notificationService = notificationService;
    }

    public List<WithdrawalResponse> getWithdrawalsByUser(String userId) {
        return withdrawalRepository.findByUserId(userId).stream()
                .map(withdrawalMapper::toResponse)
                .toList();
    }

    public WithdrawalResponse getWithdrawalById(String id, String userId) {
        Withdrawal withdrawal = getOwnedWithdrawal(id, userId);
        return withdrawalMapper.toResponse(withdrawal);
    }

    public WithdrawalResponse createWithdrawal(CreateWithdrawalRequest request, String idempotencyKeyHeader, String userId) {
        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            Optional<IdempotencyKey> existingKey = idempotencyRepository.findByKey(idempotencyKeyHeader);
            if (existingKey.isPresent()) {
                IdempotencyKey ik = existingKey.get();
                if ("COMPLETED".equals(ik.getStatus()) && ik.getResponseReference() != null) {
                    log.info("Idempotent request detected for key {}. Returning existing withdrawal {}",
                            idempotencyKeyHeader, ik.getResponseReference());
                    return getWithdrawalById(ik.getResponseReference(), userId);
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
        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds in account " + account.getId()
                            + " (required: " + request.getAmount() + " " + request.getCurrency() + ")");
        }

        Instant now = Instant.now();
        Withdrawal pending = Withdrawal.builder()
                .userId(userId)
                .accountId(account.getId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .destination(request.getDestination())
                .status("PENDING")
                .idempotencyKey(idempotencyKeyHeader)
                .createdAt(now)
                .build();
        Withdrawal savedPending = withdrawalRepository.save(pending);

        try {
            accountRepository.applyBalanceDelta(account.getId(), request.getAmount().negate());
        } catch (RuntimeException e) {
            Withdrawal failed = savedPending.toBuilder().status("FAILED").completedAt(Instant.now()).build();
            withdrawalRepository.save(failed);
            auditLogRepository.save(AuditLog.builder()
                    .userId(userId)
                    .action("WITHDRAWAL_FAILED")
                    .resourceType("WITHDRAWAL")
                    .resourceId(failed.getId())
                    .status("FAILED")
                    .timestamp(Instant.now())
                    .metadata(Map.of("accountId", account.getId(), "amount", request.getAmount().doubleValue(),
                            "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()))
                    .build());
            throw e;
        }

        Withdrawal completed = savedPending.toBuilder().status("COMPLETED").completedAt(now).build();
        Withdrawal saved = withdrawalRepository.save(completed);

        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            idempotencyRepository.save(IdempotencyKey.builder()
                    .key(idempotencyKeyHeader)
                    .userId(userId)
                    .operation("WITHDRAWAL")
                    .status("COMPLETED")
                    .responseReference(saved.getId())
                    .createdAt(now)
                    .expiresAt(now.plus(24, ChronoUnit.HOURS))
                    .build());
        }

        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action("WITHDRAWAL")
                .resourceType("WITHDRAWAL")
                .resourceId(saved.getId())
                .status("SUCCESS")
                .timestamp(now)
                .metadata(Map.of("accountId", account.getId(), "amount", request.getAmount().doubleValue(),
                        "destination", request.getDestination()))
                .build());

        notificationService.notify(userId, "WITHDRAWAL_COMPLETED", "Retiro completado",
                "Retiraste " + request.getAmount() + " " + request.getCurrency() + " de tu cuenta",
                "WITHDRAWAL", saved.getId());

        log.info("Withdrawal {} completed: {} {} from account {}", saved.getId(), request.getAmount(),
                request.getCurrency(), account.getId());

        return withdrawalMapper.toResponse(saved);
    }

    public WithdrawalResponse cancelWithdrawal(String id, String userId) {
        Withdrawal withdrawal = withdrawalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal not found with ID: " + id));

        if (!withdrawal.getUserId().equals(userId)) {
            throw new UnauthorizedOperationException("You do not own this withdrawal");
        }
        if (!"PENDING".equalsIgnoreCase(withdrawal.getStatus())) {
            throw new InvalidTransactionException("Only PENDING withdrawals can be cancelled");
        }

        Withdrawal cancelled = withdrawal.toBuilder().status("CANCELLED").completedAt(Instant.now()).build();
        Withdrawal saved = withdrawalRepository.save(cancelled);

        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action("WITHDRAWAL_CANCELLED")
                .resourceType("WITHDRAWAL")
                .resourceId(id)
                .status("SUCCESS")
                .timestamp(Instant.now())
                .metadata(Map.of("withdrawalId", id, "amount", withdrawal.getAmount().doubleValue()))
                .build());

        return withdrawalMapper.toResponse(saved);
    }

    private Withdrawal getOwnedWithdrawal(String id, String userId) {
        Withdrawal withdrawal = withdrawalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal not found with ID: " + id));

        if (userId != null && !userId.equals(withdrawal.getUserId())) {
            throw new ResourceNotFoundException("Withdrawal not found with ID: " + id);
        }

        return withdrawal;
    }
}

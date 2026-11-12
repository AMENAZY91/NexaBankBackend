package com.nexabank.api.service;

import com.nexabank.api.dto.fx.ConvertCurrencyRequest;
import com.nexabank.api.dto.fx.ConvertCurrencyResponse;
import com.nexabank.api.dto.fx.QuoteResponse;
import com.nexabank.api.dto.transfer.CreateTransferRequest;
import com.nexabank.api.dto.transfer.TransferResponse;
import com.nexabank.api.exception.InsufficientFundsException;
import com.nexabank.api.exception.InvalidCurrencyException;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.TransferMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.AuditLog;
import com.nexabank.api.model.IdempotencyKey;
import com.nexabank.api.model.Transaction;
import com.nexabank.api.model.Transfer;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.IdempotencyRepository;
import com.nexabank.api.repository.TransactionRepository;
import com.nexabank.api.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de transferencias con soporte internacional.
 *
 * <p>Valida ownership de la cuenta origen, aplica tipo de cambio y comisiones,
 * gestiona el ciclo de vida (PENDING → PROCESSING → COMPLETED/FAILED) y genera
 * número de rastreo y fecha estimada de entrega.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final AuditLogRepository auditLogRepository;
    private final TransferMapper transferMapper;
    private final FxService fxService;
    private final FeeService feeService;
    private final NotificationService notificationService;

    public TransferService(TransferRepository transferRepository,
                           AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           IdempotencyRepository idempotencyRepository,
                           AuditLogRepository auditLogRepository,
                           TransferMapper transferMapper,
                           FxService fxService,
                           FeeService feeService,
                           NotificationService notificationService) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.auditLogRepository = auditLogRepository;
        this.transferMapper = transferMapper;
        this.fxService = fxService;
        this.feeService = feeService;
        this.notificationService = notificationService;
    }

    public List<TransferResponse> getTransfersByUser(String userId) {
        return transferRepository.findByUserId(userId).stream()
                .map(transferMapper::toResponse)
                .toList();
    }

    public TransferResponse getTransferById(String id, String userId) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with ID: " + id));

        if (userId != null
                && !userId.equals(transfer.getSourceUserId())
                && !userId.equals(transfer.getDestinationUserId())) {
            throw new ResourceNotFoundException("Transfer not found with ID: " + id);
        }

        return transferMapper.toResponse(transfer);
    }

    /**
     * Obtiene una cotización (quote) previa a ejecutar la transferencia.
     */
    public QuoteResponse quoteTransfer(String userId, CreateTransferRequest request) {
        Account sourceAccount = accountRepository.findById(request.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Source account not found: " + request.getSourceAccountId()));

        if (!sourceAccount.getUserId().equals(userId)) {
            throw new UnauthorizedOperationException("The source account does not belong to the authenticated user");
        }

        if (!sourceAccount.getCurrency().equalsIgnoreCase(request.getSourceCurrency())) {
            throw new InvalidCurrencyException(
                    "Source account currency (" + sourceAccount.getCurrency() + ") does not match "
                            + "request source currency (" + request.getSourceCurrency() + ")");
        }

        Account destAccount = accountRepository.findById(request.getDestinationAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Destination account not found: " + request.getDestinationAccountId()));

        ConvertCurrencyRequest conversionRequest = ConvertCurrencyRequest.builder()
                .sourceCurrency(request.getSourceCurrency())
                .targetCurrency(request.getDestinationCurrency())
                .amount(request.getAmount())
                .build();
        ConvertCurrencyResponse conversion = fxService.convert(conversionRequest);

        BigDecimal fee = feeService.calculateTransferFee(request.getAmount(),
                request.getSourceCurrency(), request.getDestinationCurrency());
        BigDecimal total = request.getAmount().add(fee);

        String trackingNumber = "TRX-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        Instant expiresAt = Instant.now().plusSeconds(15 * 60);

        return QuoteResponse.builder()
                .quoteId("QTE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .sourceAccountId(sourceAccount.getId())
                .destinationAccountId(destAccount.getId())
                .sourceAmount(request.getAmount())
                .sourceCurrency(request.getSourceCurrency())
                .destinationAmount(conversion.getTargetAmount())
                .destinationCurrency(request.getDestinationCurrency())
                .rate(conversion.getRate())
                .fee(fee)
                .total(total)
                .expiresAt(expiresAt)
                .trackingNumber(trackingNumber)
                .build();
    }

    public TransferResponse executeTransfer(CreateTransferRequest request, String idempotencyKeyHeader, String userId) {
        // 1. Idempotencia
        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            Optional<IdempotencyKey> existingKey = idempotencyRepository.findByKey(idempotencyKeyHeader);
            if (existingKey.isPresent()) {
                IdempotencyKey ik = existingKey.get();
                if ("COMPLETED".equals(ik.getStatus()) && ik.getResponseReference() != null) {
                    log.info("Idempotent request detected for key {}. Returning existing transfer {}",
                            idempotencyKeyHeader, ik.getResponseReference());
                    return getTransferById(ik.getResponseReference(), userId);
                }
            }
        }

        // 2. Validación de ownership de la cuenta origen
        if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
            throw new InvalidTransactionException("Source and destination accounts must be different");
        }

        Account sourceAccount = accountRepository.findById(request.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Source account not found: " + request.getSourceAccountId()));

        if (!sourceAccount.getUserId().equals(userId)) {
            throw new UnauthorizedOperationException(
                    "The source account does not belong to the authenticated user");
        }

        if (!"ACTIVE".equalsIgnoreCase(sourceAccount.getStatus())) {
            throw new InvalidTransactionException("Source account is not active");
        }

        Account destAccount = accountRepository.findById(request.getDestinationAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Destination account not found: " + request.getDestinationAccountId()));

        if (!"ACTIVE".equalsIgnoreCase(destAccount.getStatus())) {
            throw new InvalidTransactionException("Destination account is not active");
        }

        // 3. Validación de monedas
        if (!sourceAccount.getCurrency().equalsIgnoreCase(request.getSourceCurrency())) {
            throw new InvalidCurrencyException(
                    "Source account currency (" + sourceAccount.getCurrency() + ") does not match "
                            + "request source currency (" + request.getSourceCurrency() + ")");
        }

        if (!destAccount.getCurrency().equalsIgnoreCase(request.getDestinationCurrency())) {
            throw new InvalidCurrencyException(
                    "Destination account currency (" + destAccount.getCurrency() + ") does not match "
                            + "request destination currency (" + request.getDestinationCurrency() + ")");
        }

        // 4. Cotización y comisiones
        ConvertCurrencyRequest conversionRequest = ConvertCurrencyRequest.builder()
                .sourceCurrency(request.getSourceCurrency())
                .targetCurrency(request.getDestinationCurrency())
                .amount(request.getAmount())
                .build();
        ConvertCurrencyResponse conversion = fxService.convert(conversionRequest);

        BigDecimal fee = feeService.calculateTransferFee(request.getAmount(),
                request.getSourceCurrency(), request.getDestinationCurrency());
        BigDecimal totalDebit = request.getAmount().add(fee);

        if (sourceAccount.getBalance().compareTo(totalDebit) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds in source account " + sourceAccount.getId()
                            + " (required: " + totalDebit + " " + request.getSourceCurrency() + ")");
        }

        BigDecimal destinationAmount = conversion.getTargetAmount();
        BigDecimal fxRate = conversion.getRate();
        BigDecimal fxMargin = feeService.calculateFxMargin(request.getAmount(), fxRate);

        // 5. Ciclo de vida: PENDING → PROCESSING
        Instant now = Instant.now();
        String trackingNumber = "TRX-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        Instant estimatedDelivery = now.plus(1, ChronoUnit.DAYS);

        Transfer pending = Transfer.builder()
                .sourceAccountId(sourceAccount.getId())
                .destinationAccountId(destAccount.getId())
                .sourceUserId(sourceAccount.getUserId())
                .destinationUserId(destAccount.getUserId())
                .amount(request.getAmount())
                .sourceCurrency(request.getSourceCurrency())
                .destinationCurrency(request.getDestinationCurrency())
                .destinationAmount(destinationAmount)
                .fee(fee)
                .fxRate(fxRate)
                .fxMargin(fxMargin)
                .status("PENDING")
                .idempotencyKey(idempotencyKeyHeader)
                .description(request.getDescription())
                .trackingNumber(trackingNumber)
                .estimatedDeliveryDate(estimatedDelivery)
                .createdAt(now)
                .senderInfo(senderInfo(sourceAccount, userId))
                .recipientInfo(recipientInfo(destAccount))
                .build();
        Transfer savedPending = transferRepository.save(pending);

        // 6. Ejecutar: PROCESSING → COMPLETED
        Transfer processed;
        try {
            processed = completeTransfer(savedPending, sourceAccount, destAccount,
                    request.getAmount(), fee, destinationAmount, fxRate, fxMargin,
                    now, idempotencyKeyHeader, userId);
        } catch (Exception e) {
            Transfer failed = Transfer.builder()
                    .id(savedPending.getId())
                    .sourceAccountId(savedPending.getSourceAccountId())
                    .destinationAccountId(savedPending.getDestinationAccountId())
                    .sourceUserId(savedPending.getSourceUserId())
                    .destinationUserId(savedPending.getDestinationUserId())
                    .amount(savedPending.getAmount())
                    .sourceCurrency(savedPending.getSourceCurrency())
                    .destinationCurrency(savedPending.getDestinationCurrency())
                    .destinationAmount(savedPending.getDestinationAmount())
                    .fee(savedPending.getFee())
                    .fxRate(savedPending.getFxRate())
                    .fxMargin(savedPending.getFxMargin())
                    .status("FAILED")
                    .idempotencyKey(savedPending.getIdempotencyKey())
                    .description(savedPending.getDescription())
                    .trackingNumber(savedPending.getTrackingNumber())
                    .estimatedDeliveryDate(savedPending.getEstimatedDeliveryDate())
                    .createdAt(savedPending.getCreatedAt())
                    .completedAt(Instant.now())
                    .senderInfo(savedPending.getSenderInfo())
                    .recipientInfo(savedPending.getRecipientInfo())
                    .build();
            transferRepository.save(failed);
            auditLogRepository.save(AuditLog.builder()
                    .userId(userId != null ? userId : sourceAccount.getUserId())
                    .action("TRANSFER_FAILED")
                    .resourceType("TRANSFER")
                    .resourceId(failed.getId())
                    .status("FAILED")
                    .timestamp(Instant.now())
                    .metadata(Map.of(
                            "sourceAccountId", sourceAccount.getId(),
                            "destinationAccountId", destAccount.getId(),
                            "amount", request.getAmount().doubleValue(),
                            "error", e.getMessage()
                    ))
                    .build());
            throw e;
        }

        return transferMapper.toResponse(processed);
    }

    private Transfer completeTransfer(Transfer pending, Account sourceAccount, Account destAccount,
                                      BigDecimal amount, BigDecimal fee, BigDecimal destinationAmount,
                                      BigDecimal fxRate, BigDecimal fxMargin, Instant now,
                                      String idempotencyKeyHeader, String userId) {
        // Marcar PROCESSING
        Transfer processing = Transfer.builder()
                .id(pending.getId())
                .sourceAccountId(pending.getSourceAccountId())
                .destinationAccountId(pending.getDestinationAccountId())
                .sourceUserId(pending.getSourceUserId())
                .destinationUserId(pending.getDestinationUserId())
                .amount(pending.getAmount())
                .sourceCurrency(pending.getSourceCurrency())
                .destinationCurrency(pending.getDestinationCurrency())
                .destinationAmount(pending.getDestinationAmount())
                .fee(pending.getFee())
                .fxRate(pending.getFxRate())
                .fxMargin(pending.getFxMargin())
                .status("PROCESSING")
                .idempotencyKey(pending.getIdempotencyKey())
                .description(pending.getDescription())
                .trackingNumber(pending.getTrackingNumber())
                .estimatedDeliveryDate(pending.getEstimatedDeliveryDate())
                .createdAt(pending.getCreatedAt())
                .senderInfo(pending.getSenderInfo())
                .recipientInfo(pending.getRecipientInfo())
                .build();
        transferRepository.save(processing);

        BigDecimal totalDebit = amount.add(fee);

        // Transferencia atómica de saldo: debitar origen y acreditar destino en una transacción
        accountRepository.transferBalance(sourceAccount.getId(), destAccount.getId(),
                totalDebit, destinationAmount);

        // Transacciones de ambos lados
        Transaction debitTx = transactionRepository.save(Transaction.builder()
                .accountId(sourceAccount.getId())
                .userId(sourceAccount.getUserId())
                .type("EXPENSE")
                .category("TRANSFER")
                .amount(totalDebit)
                .currency(requestCurrency(sourceAccount))
                .description("Transfer to " + destAccount.getId()
                        + (pending.getDescription() != null ? ": " + pending.getDescription() : ""))
                .status("COMPLETED")
                .timestamp(now)
                .createdAt(now)
                .build());

        Transaction creditTx = transactionRepository.save(Transaction.builder()
                .accountId(destAccount.getId())
                .userId(destAccount.getUserId())
                .type("INCOME")
                .category("TRANSFER")
                .amount(destinationAmount)
                .currency(destAccount.getCurrency())
                .description("Transfer from " + sourceAccount.getId()
                        + (pending.getDescription() != null ? ": " + pending.getDescription() : ""))
                .status("COMPLETED")
                .timestamp(now)
                .createdAt(now)
                .build());

        Transfer completed = Transfer.builder()
                .id(pending.getId())
                .sourceAccountId(pending.getSourceAccountId())
                .destinationAccountId(pending.getDestinationAccountId())
                .sourceUserId(pending.getSourceUserId())
                .destinationUserId(pending.getDestinationUserId())
                .amount(pending.getAmount())
                .sourceCurrency(pending.getSourceCurrency())
                .destinationCurrency(pending.getDestinationCurrency())
                .destinationAmount(destinationAmount)
                .fee(fee)
                .fxRate(fxRate)
                .fxMargin(fxMargin)
                .status("COMPLETED")
                .idempotencyKey(idempotencyKeyHeader)
                .description(pending.getDescription())
                .trackingNumber(pending.getTrackingNumber())
                .estimatedDeliveryDate(pending.getEstimatedDeliveryDate())
                .createdAt(pending.getCreatedAt())
                .completedAt(now)
                .senderInfo(pending.getSenderInfo())
                .recipientInfo(pending.getRecipientInfo())
                .build();

        Transfer saved = transferRepository.save(completed);

        // Idempotencia
        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            idempotencyRepository.save(IdempotencyKey.builder()
                    .key(idempotencyKeyHeader)
                    .userId(userId != null ? userId : sourceAccount.getUserId())
                    .operation("TRANSFER")
                    .status("COMPLETED")
                    .responseReference(saved.getId())
                    .createdAt(now)
                    .expiresAt(now.plus(24, ChronoUnit.HOURS))
                    .build());
        }

        // Auditoría
        auditLogRepository.save(AuditLog.builder()
                .userId(userId != null ? userId : sourceAccount.getUserId())
                .action("TRANSFER")
                .resourceType("TRANSFER")
                .resourceId(saved.getId())
                .status("SUCCESS")
                .timestamp(now)
                .metadata(Map.of(
                        "sourceAccountId", sourceAccount.getId(),
                        "destinationAccountId", destAccount.getId(),
                        "amount", amount.doubleValue(),
                        "fee", fee.doubleValue(),
                        "fxRate", fxRate.doubleValue(),
                        "destinationAmount", destinationAmount.doubleValue(),
                        "debitTransactionId", debitTx.getId(),
                        "creditTransactionId", creditTx.getId()
                ))
                .build());

        log.info("Transfer {} completed: {} {} → {} {} (fee {} {}, fxRate {})",
                saved.getId(), amount, pending.getSourceCurrency(), destinationAmount,
                pending.getDestinationCurrency(), fee, pending.getSourceCurrency(), fxRate);

        notificationService.notify(sourceAccount.getUserId(), "TRANSFER_SENT",
                "Transferencia enviada",
                "Enviaste " + amount + " " + pending.getSourceCurrency() + " a " + destAccount.getName(),
                "TRANSFER", saved.getId());
        notificationService.notify(destAccount.getUserId(), "TRANSFER_RECEIVED",
                "Transferencia recibida",
                "Recibiste " + destinationAmount + " " + pending.getDestinationCurrency() + " de " + sourceAccount.getName(),
                "TRANSFER", saved.getId());

        return saved;
    }

    private String requestCurrency(Account account) {
        return account.getCurrency();
    }

    private Map<String, String> senderInfo(Account account, String userId) {
        Map<String, String> info = new HashMap<>();
        info.put("accountId", account.getId());
        info.put("userId", userId != null ? userId : account.getUserId());
        info.put("currency", account.getCurrency());
        info.put("name", account.getName());
        return info;
    }

    private Map<String, String> recipientInfo(Account account) {
        Map<String, String> info = new HashMap<>();
        info.put("accountId", account.getId());
        info.put("userId", account.getUserId());
        info.put("currency", account.getCurrency());
        info.put("name", account.getName());
        return info;
    }

    public TransferResponse cancelTransfer(String transferId, String userId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with ID: " + transferId));

        if (!transfer.getSourceUserId().equals(userId)) {
            throw new UnauthorizedOperationException("You do not own this transfer");
        }

        if (!"PENDING".equalsIgnoreCase(transfer.getStatus())) {
            throw new InvalidTransactionException("Only PENDING transfers can be cancelled");
        }

        Transfer cancelled = Transfer.builder()
                .id(transfer.getId())
                .sourceAccountId(transfer.getSourceAccountId())
                .destinationAccountId(transfer.getDestinationAccountId())
                .sourceUserId(transfer.getSourceUserId())
                .destinationUserId(transfer.getDestinationUserId())
                .amount(transfer.getAmount())
                .sourceCurrency(transfer.getSourceCurrency())
                .destinationCurrency(transfer.getDestinationCurrency())
                .destinationAmount(transfer.getDestinationAmount())
                .fee(transfer.getFee())
                .fxRate(transfer.getFxRate())
                .fxMargin(transfer.getFxMargin())
                .status("CANCELLED")
                .idempotencyKey(transfer.getIdempotencyKey())
                .description(transfer.getDescription())
                .trackingNumber(transfer.getTrackingNumber())
                .estimatedDeliveryDate(transfer.getEstimatedDeliveryDate())
                .createdAt(transfer.getCreatedAt())
                .completedAt(Instant.now())
                .senderInfo(transfer.getSenderInfo())
                .recipientInfo(transfer.getRecipientInfo())
                .build();

        transferRepository.save(cancelled);
        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action("TRANSFER_CANCELLED")
                .resourceType("TRANSFER")
                .resourceId(transferId)
                .status("SUCCESS")
                .timestamp(Instant.now())
                .metadata(Map.of("transferId", transferId, "amount", transfer.getAmount().doubleValue()))
                .build());

        return transferMapper.toResponse(cancelled);
    }
}

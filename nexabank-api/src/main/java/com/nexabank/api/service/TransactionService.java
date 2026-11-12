package com.nexabank.api.service;

import com.nexabank.api.dto.transaction.CreateTransactionRequest;
import com.nexabank.api.dto.transaction.TransactionResponse;
import com.nexabank.api.exception.InsufficientFundsException;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.mapper.TransactionMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.AuditLog;
import com.nexabank.api.model.Transaction;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final TransactionMapper transactionMapper;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              AuditLogRepository auditLogRepository,
                              TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
        this.transactionMapper = transactionMapper;
    }

    public List<TransactionResponse> getTransactions(String accountId, String userId) {
        if (accountId != null && !accountId.isBlank()) {
            return transactionRepository.findByAccountId(accountId).stream()
                    .map(transactionMapper::toResponse)
                    .toList();
        }
        if (userId != null && !userId.isBlank()) {
            return transactionRepository.findByUserId(userId).stream()
                    .map(transactionMapper::toResponse)
                    .toList();
        }
        return List.of();
    }

    /**
     * Resultado de una página obtenida por cursor: respuestas ya mapeadas + cursor siguiente.
     */
    public record TransactionCursorPage(List<TransactionResponse> items, String nextCursor) {
    }

    /**
     * Pagina transacciones usando cursor real de Firestore ({@code startAfter}/{@code limit}).
     *
     * @param accountId filtro por cuenta (prioritario sobre userId si no está en blanco)
     * @param userId    filtro por usuario
     * @param cursor    ID del último documento visto en la página anterior (null para la primera página)
     * @param limit     tamaño de página
     * @return página de resultados mapeados + cursor de la siguiente página (o null si no hay más)
     */
    public TransactionCursorPage getTransactionsByCursor(String accountId, String userId, String cursor, int limit) {
        TransactionRepository.CursorPage page;
        if (accountId != null && !accountId.isBlank()) {
            page = transactionRepository.findByAccountIdAfter(accountId, cursor, limit);
        } else if (userId != null && !userId.isBlank()) {
            page = transactionRepository.findByUserIdAfter(userId, cursor, limit);
        } else {
            page = new TransactionRepository.CursorPage(List.of(), null);
        }
        List<TransactionResponse> mapped = page.items().stream()
                .map(transactionMapper::toResponse)
                .toList();
        return new TransactionCursorPage(mapped, page.nextCursor());
    }

    public TransactionResponse getTransactionById(String id, String userId) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + id));

        if (userId != null && !userId.equals(tx.getUserId())) {
            throw new ResourceNotFoundException("Transaction not found with ID: " + id);
        }

        return transactionMapper.toResponse(tx);
    }

    public List<Transaction> getAllTransactionEntities(String accountId, String userId) {
        if (accountId != null && !accountId.isBlank()) {
            return transactionRepository.findByAccountId(accountId);
        }
        if (userId != null && !userId.isBlank()) {
            return transactionRepository.findByUserId(userId);
        }
        return List.of();
    }

    public TransactionResponse createTransaction(CreateTransactionRequest request, String userId) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.getAccountId()));

        if (!account.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw new InvalidTransactionException("Currency mismatch: account=" + account.getCurrency() + ", request=" + request.getCurrency());
        }

        String type = request.getType().toUpperCase();
        BigDecimal delta;
        if ("EXPENSE".equals(type)) {
            delta = request.getAmount().negate();
        } else if ("INCOME".equals(type)) {
            delta = request.getAmount();
        } else {
            throw new InvalidTransactionException("Unsupported transaction type: " + type);
        }

        BigDecimal previousBalance = account.getBalance();

        // 1. Guardar transacción con estado COMPLETED
        Transaction tx = transactionMapper.fromCreateRequest(request, userId != null ? userId : account.getUserId());
        tx.setStatus("COMPLETED");
        Transaction savedTx = transactionRepository.save(tx);

        // 2. Mutar el saldo de forma atómica: relee el balance y re-valida fondos suficientes
        // dentro de la misma transacción de Firestore (mejora #15), evitando condiciones de
        // carrera entre lecturas concurrentes del balance.
        BigDecimal newBalance;
        try {
            newBalance = accountRepository.applyBalanceDelta(account.getId(), delta);
        } catch (InsufficientFundsException e) {
            throw new InsufficientFundsException(
                    "Insufficient funds: current balance " + previousBalance + " is less than " + request.getAmount());
        }

        // 3. Registrar auditoría
        auditLogRepository.save(AuditLog.builder()
                .userId(userId != null ? userId : account.getUserId())
                .action("CREATE_TRANSACTION")
                .resourceType("TRANSACTION")
                .resourceId(savedTx.getId())
                .status("SUCCESS")
                .timestamp(Instant.now())
                .metadata(Map.of(
                        "accountId", account.getId(),
                        "amount", request.getAmount().doubleValue(),
                        "type", type,
                        "previousBalance", previousBalance.doubleValue(),
                        "newBalance", newBalance.doubleValue()
                ))
                .build());

        log.info("Processed {} of {} on account {}. New balance: {}", type, request.getAmount(), account.getId(), newBalance);
        return transactionMapper.toResponse(savedTx);
    }
}

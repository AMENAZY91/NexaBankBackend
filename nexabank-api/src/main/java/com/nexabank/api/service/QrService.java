package com.nexabank.api.service;

import com.nexabank.api.dto.qr.QrPayRequest;
import com.nexabank.api.exception.InsufficientFundsException;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.Transaction;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.TransactionRepository;
import com.nexabank.api.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de pagos por QR.
 */
@Service
public class QrService {

    private static final Logger log = LoggerFactory.getLogger(QrService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final SecurityUtils securityUtils;
    private final NotificationService notificationService;

    public QrService(AccountRepository accountRepository,
                     TransactionRepository transactionRepository,
                     SecurityUtils securityUtils,
                     NotificationService notificationService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.securityUtils = securityUtils;
        this.notificationService = notificationService;
    }

    public Map<String, Object> processQrPayment(QrPayRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        String userId = securityUtils.getCurrentUserId();

        // Decodificar QR
        Map<String, Object> qrData = decodeQr(request.getQrData());
        String destinationAccountId = (String) qrData.get("accountId");
        String merchantName = (String) qrData.get("merchant");

        if (destinationAccountId == null) {
            result.put("success", false);
            result.put("error", "Invalid QR code");
            return result;
        }

        // Buscar cuenta origen del usuario
        Account sourceAccount = accountRepository.findByUserId(userId).stream()
                .findFirst()
                .orElse(null);

        if (sourceAccount == null) {
            result.put("success", false);
            result.put("error", "No source account found");
            return result;
        }

        BigDecimal amount = new BigDecimal(request.getAmount());

        // Débito atómico: relee el balance y re-valida fondos suficientes dentro de la misma
        // transacción de Firestore (mejora #15), evitando que dos pagos QR concurrentes sobre
        // la misma cuenta produzcan un "lost update" / doble gasto.
        BigDecimal newBalance;
        try {
            newBalance = accountRepository.applyBalanceDelta(sourceAccount.getId(), amount.negate());
        } catch (InsufficientFundsException e) {
            result.put("success", false);
            result.put("error", "Insufficient funds");
            return result;
        }
        sourceAccount.setBalance(newBalance);

        // Crear transacción
        Transaction tx = Transaction.builder()
                .id("TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .accountId(sourceAccount.getId())
                .userId(userId)
                .type("EXPENSE")
                .category("QR_PAYMENT")
                .amount(amount)
                .currency(request.getCurrency() != null ? request.getCurrency() : "COP")
                .description(request.getDescription() != null ? request.getDescription() : "Pago QR a " + merchantName)
                .status("COMPLETED")
                .timestamp(Instant.now())
                .createdAt(Instant.now())
                .build();
        transactionRepository.save(tx);

        notificationService.notify(userId, "QR_PAYMENT",
                "Pago QR realizado",
                "Pagaste " + amount + " " + tx.getCurrency() + " a " + merchantName,
                "TRANSACTION", tx.getId());

        result.put("success", true);
        result.put("transactionId", tx.getId());
        result.put("amount", amount);
        result.put("merchant", merchantName);
        result.put("destinationAccount", destinationAccountId);
        result.put("newBalance", sourceAccount.getBalance());
        result.put("timestamp", Instant.now().toString());
        return result;
    }

    public Map<String, Object> decodeQr(String data) {
        Map<String, Object> decoded = new LinkedHashMap<>();
        // Formato QR: "nexabank:pay:ACC-123456:MerchantName"
        if (data != null && data.startsWith("nexabank:pay:")) {
            String[] parts = data.split(":");
            if (parts.length >= 3) {
                decoded.put("accountId", parts[2]);
                decoded.put("merchant", parts.length > 3 ? parts[3] : "Unknown");
            }
        } else {
            decoded.put("accountId", data);
            decoded.put("merchant", "Unknown");
        }
        return decoded;
    }
}
package com.nexabank.api.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.nexabank.api.exception.InsufficientFundsException;
import com.nexabank.api.model.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
/**
 * Repository para la colección {@code accounts} de Firestore.
 */
@Repository
public class AccountRepository {

    private static final Logger log = LoggerFactory.getLogger(AccountRepository.class);
    private static final String COLLECTION = "accounts";

    private final Firestore firestore;

    public AccountRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<Account> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
            if (doc.exists()) {
                return Optional.of(documentToAccount(doc));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding account: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public List<Account> findByUserId(String userId) {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION)
                    .whereEqualTo("userId", userId)
                    .get().get();
            return query.getDocuments().stream()
                    .map(this::documentToAccount)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding accounts for user: {}", userId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public Account save(Account account) {
        try {
            if (account.getId() == null) {
                account.setId(generateId());
            }
            firestore.collection(COLLECTION).document(account.getId())
                    .set(accountToMap(account)).get();
            log.debug("Saved account: {}", account.getId());
            return account;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error saving account: {}", account.getId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    public void updateBalance(String id, BigDecimal newBalance) {
        try {
            firestore.collection(COLLECTION).document(id).update(
                    Map.of(
                            "balance", newBalance.toPlainString(),
                            "updatedAt", Timestamp.now()
                    )
            ).get();
            log.debug("Updated balance for account {}: {}", id, newBalance);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error updating balance for account: {}", id, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    /**
     * Aplica un delta (positivo o negativo) al balance de una cuenta de forma atómica usando
     * una transacción de Firestore: relee el balance dentro de la transacción, valida fondos
     * suficientes si el delta es negativo, y escribe el nuevo balance (mejora #15).
     *
     * <p>Usado por mutaciones de balance de una sola cuenta: {@code TransactionService}
     * (ingresos/egresos manuales) y {@code QrService} (pagos QR).
     *
     * @param accountId cuenta a mutar
     * @param delta     monto a sumar (positivo = crédito/INCOME) o restar (negativo = débito/EXPENSE)
     * @return el balance resultante tras aplicar el delta
     */
    public BigDecimal applyBalanceDelta(String accountId, BigDecimal delta) {
        try {
            return firestore.runTransaction(transaction -> {
                DocumentReference ref = firestore.collection(COLLECTION).document(accountId);
                DocumentSnapshot doc = transaction.get(ref).get();

                if (!doc.exists()) {
                    throw new RuntimeException("Account not found: " + accountId);
                }

                BigDecimal currentBalance = readBalance(doc);
                BigDecimal newBalance = currentBalance.add(delta);

                if (delta.signum() < 0 && currentBalance.compareTo(delta.negate()) < 0) {
                    throw new InsufficientFundsException(
                            "Insufficient funds in account " + accountId
                                    + " (required: " + delta.negate() + ", available: " + currentBalance + ")");
                }

                transaction.update(ref, Map.of(
                        "balance", newBalance.toPlainString(),
                        "updatedAt", Timestamp.now()
                ));

                return newBalance;
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        } catch (ExecutionException e) {
            log.error("Atomic balance delta failed for account: {}", accountId, e.getCause());
            if (e.getCause() instanceof InsufficientFundsException ife) {
                throw ife;
            }
            throw new RuntimeException("Error writing to Firestore", e.getCause());
        }
    }

    public void updateStatus(String id, String status) {
        try {
            firestore.collection(COLLECTION).document(id).update(
                    Map.of(
                            "status", status,
                            "updatedAt", Timestamp.now()
                    )
            ).get();
            log.debug("Updated status for account {}: {}", id, status);
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Error updating status for account: {}", id, e);
            throw new RuntimeException("Error writing to Firestore", e);
        }
    }

    /**
     * Transfiere saldo de forma atómica entre dos cuentas usando una transacción de Firestore.
     * Debita el origen y acredita el destino en una sola operación transaccional.
     *
     * @param sourceAccountId cuenta origen
     * @param destAccountId   cuenta destino
     * @param totalDebit      monto a debitar del origen (amount + fee)
     * @param destinationAmount monto a acreditar al destino (monto convertido)
     */
    public void transferBalance(String sourceAccountId, String destAccountId,
                                BigDecimal totalDebit, BigDecimal destinationAmount) {
        try {
            firestore.runTransaction(transaction -> {
                DocumentSnapshot sourceDoc = transaction.get(
                        firestore.collection(COLLECTION).document(sourceAccountId)).get();
                DocumentSnapshot destDoc = transaction.get(
                        firestore.collection(COLLECTION).document(destAccountId)).get();

                if (!sourceDoc.exists()) {
                    throw new RuntimeException("Source account not found: " + sourceAccountId);
                }
                if (!destDoc.exists()) {
                    throw new RuntimeException("Destination account not found: " + destAccountId);
                }

                BigDecimal sourceBalance = readBalance(sourceDoc);
                if (sourceBalance.compareTo(totalDebit) < 0) {
                    throw new InsufficientFundsException(
                            "Insufficient funds in source account " + sourceAccountId
                                    + " (required: " + totalDebit + ", available: " + sourceBalance + ")");
                }

                transaction.update(
                        firestore.collection(COLLECTION).document(sourceAccountId),
                        Map.of(
                                "balance", sourceBalance.subtract(totalDebit).toPlainString(),
                                "updatedAt", Timestamp.now()
                        )
                );

                BigDecimal destBalance = readBalance(destDoc);
                transaction.update(
                        firestore.collection(COLLECTION).document(destAccountId),
                        Map.of(
                                "balance", destBalance.add(destinationAmount).toPlainString(),
                                "updatedAt", Timestamp.now()
                        )
                );

                return null;
            });
            log.debug("Atomic balance transfer: {} -> {} (debit {}, credit {})",
                    sourceAccountId, destAccountId, totalDebit, destinationAmount);
        } catch (Exception e) {
            log.error("Atomic balance transfer failed: {} -> {}", sourceAccountId, destAccountId, e.getMessage());
            throw e;
        }
    }

    public List<Account> findAll() {
        try {
            QuerySnapshot query = firestore.collection(COLLECTION).get().get();
            return query.getDocuments().stream()
                    .map(this::documentToAccount)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error finding all accounts", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    public long count() {
        try {
            return firestore.collection(COLLECTION).get().get().size();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error counting accounts", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    private String generateId() {
        String id;
        int attempts = 0;
        do {
            id = "ACC-" + String.format("%06d", (int) (Math.random() * 999999) + 1);
            attempts++;
        } while (exists(id) && attempts < 10);
        return id;
    }

    private boolean exists(String id) {
        try {
            return firestore.collection(COLLECTION).document(id).get().get().exists();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    private Account documentToAccount(DocumentSnapshot doc) {
        return Account.builder()
                .id(doc.getString("id"))
                .userId(doc.getString("userId"))
                .type(doc.getString("type"))
                .name(doc.getString("name"))
                .currency(doc.getString("currency"))
                .balance(readBalance(doc))
                .status(doc.getString("status"))
                .createdAt(toInstant(doc.getTimestamp("createdAt")))
                .updatedAt(toInstant(doc.getTimestamp("updatedAt")))
                .build();
    }

    private Map<String, Object> accountToMap(Account account) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", account.getId());
        map.put("userId", account.getUserId());
        map.put("type", account.getType());
        map.put("name", account.getName() != null ? account.getName() : "");
        map.put("currency", account.getCurrency());
        map.put("balance", account.getBalance().toPlainString());
        map.put("status", account.getStatus());
        map.put("createdAt", account.getCreatedAt() != null ? Timestamp.ofTimeSecondsAndNanos(account.getCreatedAt().getEpochSecond(), account.getCreatedAt().getNano()) : Timestamp.now());
        map.put("updatedAt", Timestamp.now());
        return map;
    }

    /**
     * Lee el campo {@code balance} soportando ambos formatos durante la transición de la
     * mejora #16 (double legacy → String decimal exacto): intenta leer como {@code String}
     * primero (formato nuevo); si no es un String (documento legado aún no migrado), cae a
     * {@code getDouble}. Ver docs/DECISIONS.md para la justificación de este dual-read en vez
     * de purga+resembrado obligatorio.
     */
    private BigDecimal readBalance(DocumentSnapshot doc) {
        Object raw = doc.get("balance");
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        if (raw instanceof String s) {
            return new BigDecimal(s);
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}

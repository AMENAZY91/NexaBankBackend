package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexabank.api.dto.transaction.CreateTransactionRequest;
import com.nexabank.api.dto.transaction.TransactionResponse;
import com.nexabank.api.exception.InsufficientFundsException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.mapper.TransactionMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.Transaction;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private TransactionMapper transactionMapper;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository, accountRepository,
                auditLogRepository, transactionMapper);
    }

    @Test
    void getTransactionById_shouldReturn_whenOwnedByUser() {
        Transaction tx = Transaction.builder().id("TX-000001").userId("USR-000001")
                .amount(new BigDecimal("100.00")).build();
        when(transactionRepository.findById("TX-000001")).thenReturn(Optional.of(tx));
        when(transactionMapper.toResponse(tx)).thenReturn(TransactionResponse.builder().id("TX-000001").build());

        TransactionResponse result = transactionService.getTransactionById("TX-000001", "USR-000001");

        assertThat(result.getId()).isEqualTo("TX-000001");
    }

    @Test
    void getTransactionById_shouldThrowNotFound_whenNotOwnedByUser() {
        Transaction tx = Transaction.builder().id("TX-000001").userId("USR-000001")
                .amount(new BigDecimal("100.00")).build();
        when(transactionRepository.findById("TX-000001")).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> transactionService.getTransactionById("TX-000001", "USR-999999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTransactionById_shouldThrowNotFound_whenMissing() {
        when(transactionRepository.findById("TX-999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransactionById("TX-999999", "USR-000001"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTransaction_shouldUseAtomicBalanceDelta_notSeparateGetAndUpdate() {
        Account account = Account.builder().id("ACC-000001").userId("USR-000001")
                .currency("COP").balance(new BigDecimal("1000000.00")).build();
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(account));
        when(accountRepository.applyBalanceDelta(eq("ACC-000001"), eq(new BigDecimal("-50000.00"))))
                .thenReturn(new BigDecimal("950000.00"));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            tx.setId("TX-000001");
            return tx;
        });
        when(transactionMapper.fromCreateRequest(any(), any())).thenReturn(Transaction.builder().build());
        when(transactionMapper.toResponse(any())).thenReturn(TransactionResponse.builder().id("TX-000001").build());

        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .accountId("ACC-000001").type("EXPENSE")
                .amount(new BigDecimal("50000.00")).currency("COP").build();

        TransactionResponse result = transactionService.createTransaction(request, "USR-000001");

        assertThat(result.getId()).isEqualTo("TX-000001");
        // Mejora #15: el balance se muta vía applyBalanceDelta (transacción Firestore atómica),
        // nunca con updateBalance como llamada separada tras un findById previo.
        verify(accountRepository).applyBalanceDelta("ACC-000001", new BigDecimal("-50000.00"));
        verify(accountRepository, never()).updateBalance(any(), any());
    }

    @Test
    void createTransaction_shouldApplyPositiveDelta_forIncome() {
        Account account = Account.builder().id("ACC-000001").userId("USR-000001")
                .currency("COP").balance(new BigDecimal("1000000.00")).build();
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(account));
        when(accountRepository.applyBalanceDelta(eq("ACC-000001"), eq(new BigDecimal("50000.00"))))
                .thenReturn(new BigDecimal("1050000.00"));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionMapper.fromCreateRequest(any(), any())).thenReturn(Transaction.builder().build());
        when(transactionMapper.toResponse(any())).thenReturn(TransactionResponse.builder().build());

        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .accountId("ACC-000001").type("INCOME")
                .amount(new BigDecimal("50000.00")).currency("COP").build();

        transactionService.createTransaction(request, "USR-000001");

        verify(accountRepository).applyBalanceDelta("ACC-000001", new BigDecimal("50000.00"));
    }

    @Test
    void createTransaction_shouldPropagateInsufficientFunds_fromAtomicDelta() {
        Account account = Account.builder().id("ACC-000001").userId("USR-000001")
                .currency("COP").balance(new BigDecimal("1000.00")).build();
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(account));
        when(accountRepository.applyBalanceDelta(eq("ACC-000001"), any()))
                .thenThrow(new InsufficientFundsException("Insufficient funds"));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionMapper.fromCreateRequest(any(), any())).thenReturn(Transaction.builder().build());

        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .accountId("ACC-000001").type("EXPENSE")
                .amount(new BigDecimal("50000.00")).currency("COP").build();

        assertThatThrownBy(() -> transactionService.createTransaction(request, "USR-000001"))
                .isInstanceOf(InsufficientFundsException.class);
    }
}

package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexabank.api.dto.withdrawal.CreateWithdrawalRequest;
import com.nexabank.api.dto.withdrawal.WithdrawalResponse;
import com.nexabank.api.exception.InsufficientFundsException;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.WithdrawalMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.IdempotencyKey;
import com.nexabank.api.model.Withdrawal;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.IdempotencyRepository;
import com.nexabank.api.repository.WithdrawalRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock
    private WithdrawalRepository withdrawalRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private IdempotencyRepository idempotencyRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private NotificationService notificationService;

    private WithdrawalService withdrawalService;

    @BeforeEach
    void setUp() {
        withdrawalService = new WithdrawalService(withdrawalRepository, accountRepository, idempotencyRepository,
                auditLogRepository, new WithdrawalMapper(), notificationService);
    }

    private Account activeAccount(BigDecimal balance) {
        return Account.builder().id("ACC-000001").userId("USR-000001")
                .currency("COP").balance(balance).status("ACTIVE").build();
    }

    @Test
    void createWithdrawal_shouldSucceed_whenSufficientFunds() {
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(activeAccount(new BigDecimal("100000.00"))));
        when(withdrawalRepository.save(any())).thenAnswer(inv -> {
            Withdrawal w = inv.getArgument(0);
            if (w.getId() == null) w.setId("WDR-00000001");
            return w;
        });

        CreateWithdrawalRequest request = CreateWithdrawalRequest.builder()
                .accountId("ACC-000001").amount(new BigDecimal("50000.00"))
                .currency("COP").destination("BANK_TRANSFER").build();

        WithdrawalResponse result = withdrawalService.createWithdrawal(request, null, "USR-000001");

        assertThat(result.getId()).isEqualTo("WDR-00000001");
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(accountRepository).applyBalanceDelta("ACC-000001", new BigDecimal("-50000.00"));
        verify(notificationService).notify(eq("USR-000001"), eq("WITHDRAWAL_COMPLETED"), anyString(), anyString(),
                eq("WITHDRAWAL"), eq("WDR-00000001"));
    }

    @Test
    void createWithdrawal_shouldThrowInsufficientFunds_beforeCreatingRecord() {
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(activeAccount(new BigDecimal("1000.00"))));

        CreateWithdrawalRequest request = CreateWithdrawalRequest.builder()
                .accountId("ACC-000001").amount(new BigDecimal("50000.00"))
                .currency("COP").destination("BANK_TRANSFER").build();

        assertThatThrownBy(() -> withdrawalService.createWithdrawal(request, null, "USR-000001"))
                .isInstanceOf(InsufficientFundsException.class);

        // Pre-check falla antes de crear ningún registro PENDING, igual que TransferService.
        verify(withdrawalRepository, never()).save(any());
        verify(accountRepository, never()).applyBalanceDelta(any(), any());
    }

    @Test
    void createWithdrawal_shouldThrowUnauthorized_whenAccountNotOwned() {
        Account other = Account.builder().id("ACC-000001").userId("USR-999999")
                .currency("COP").balance(new BigDecimal("100000.00")).status("ACTIVE").build();
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(other));

        CreateWithdrawalRequest request = CreateWithdrawalRequest.builder()
                .accountId("ACC-000001").amount(new BigDecimal("50000.00"))
                .currency("COP").destination("BANK_TRANSFER").build();

        assertThatThrownBy(() -> withdrawalService.createWithdrawal(request, null, "USR-000001"))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void createWithdrawal_shouldReturnExisting_whenIdempotentKeyCompleted() {
        when(idempotencyRepository.findByKey("key-123")).thenReturn(Optional.of(
                IdempotencyKey.builder().key("key-123").status("COMPLETED").responseReference("WDR-00000001").build()));
        when(withdrawalRepository.findById("WDR-00000001")).thenReturn(Optional.of(
                Withdrawal.builder().id("WDR-00000001").userId("USR-000001").status("COMPLETED").build()));

        CreateWithdrawalRequest request = CreateWithdrawalRequest.builder()
                .accountId("ACC-000001").amount(new BigDecimal("50000.00"))
                .currency("COP").destination("BANK_TRANSFER").build();

        WithdrawalResponse result = withdrawalService.createWithdrawal(request, "key-123", "USR-000001");

        assertThat(result.getId()).isEqualTo("WDR-00000001");
        verify(accountRepository, never()).findById(anyString());
    }

    @Test
    void cancelWithdrawal_shouldSucceed_whenPendingAndOwned() {
        Withdrawal pending = Withdrawal.builder().id("WDR-00000001").userId("USR-000001")
                .amount(new BigDecimal("100.00")).status("PENDING").build();
        when(withdrawalRepository.findById("WDR-00000001")).thenReturn(Optional.of(pending));
        when(withdrawalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WithdrawalResponse result = withdrawalService.cancelWithdrawal("WDR-00000001", "USR-000001");

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelWithdrawal_shouldThrowUnauthorized_whenNotOwned() {
        Withdrawal pending = Withdrawal.builder().id("WDR-00000001").userId("USR-000001").status("PENDING").build();
        when(withdrawalRepository.findById("WDR-00000001")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> withdrawalService.cancelWithdrawal("WDR-00000001", "USR-999999"))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void cancelWithdrawal_shouldThrow_whenNotPending() {
        Withdrawal completed = Withdrawal.builder().id("WDR-00000001").userId("USR-000001").status("COMPLETED").build();
        when(withdrawalRepository.findById("WDR-00000001")).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> withdrawalService.cancelWithdrawal("WDR-00000001", "USR-000001"))
                .isInstanceOf(InvalidTransactionException.class);
    }
}

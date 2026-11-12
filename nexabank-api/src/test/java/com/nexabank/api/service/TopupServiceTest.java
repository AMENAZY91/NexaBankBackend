package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexabank.api.dto.topup.CreateTopupRequest;
import com.nexabank.api.dto.topup.TopupResponse;
import com.nexabank.api.exception.InvalidCurrencyException;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.TopupMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.IdempotencyKey;
import com.nexabank.api.model.Topup;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.IdempotencyRepository;
import com.nexabank.api.repository.TopupRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopupServiceTest {

    @Mock
    private TopupRepository topupRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private IdempotencyRepository idempotencyRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private NotificationService notificationService;

    private TopupService topupService;

    @BeforeEach
    void setUp() {
        topupService = new TopupService(topupRepository, accountRepository, idempotencyRepository,
                auditLogRepository, new TopupMapper(), notificationService);
    }

    private Account activeAccount() {
        return Account.builder().id("ACC-000001").userId("USR-000001")
                .currency("COP").balance(new BigDecimal("100000.00")).status("ACTIVE").build();
    }

    @Test
    void createTopup_shouldSucceed_whenAccountOwnedAndActive() {
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(activeAccount()));
        when(topupRepository.save(any())).thenAnswer(inv -> {
            Topup t = inv.getArgument(0);
            if (t.getId() == null) t.setId("TOP-00000001");
            return t;
        });

        CreateTopupRequest request = CreateTopupRequest.builder()
                .accountId("ACC-000001").amount(new BigDecimal("50000.00"))
                .currency("COP").source("CARD").build();

        TopupResponse result = topupService.createTopup(request, null, "USR-000001");

        assertThat(result.getId()).isEqualTo("TOP-00000001");
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(accountRepository).applyBalanceDelta("ACC-000001", new BigDecimal("50000.00"));
        verify(notificationService).notify(eq("USR-000001"), eq("TOPUP_COMPLETED"), anyString(), anyString(),
                eq("TOPUP"), eq("TOP-00000001"));
        verify(auditLogRepository).save(any());
    }

    @Test
    void createTopup_shouldReturnExisting_whenIdempotentKeyCompleted() {
        when(idempotencyRepository.findByKey("key-123")).thenReturn(Optional.of(
                IdempotencyKey.builder().key("key-123").status("COMPLETED").responseReference("TOP-00000001").build()));
        when(topupRepository.findById("TOP-00000001")).thenReturn(Optional.of(
                Topup.builder().id("TOP-00000001").userId("USR-000001").status("COMPLETED").build()));

        CreateTopupRequest request = CreateTopupRequest.builder()
                .accountId("ACC-000001").amount(new BigDecimal("50000.00"))
                .currency("COP").source("CARD").build();

        TopupResponse result = topupService.createTopup(request, "key-123", "USR-000001");

        assertThat(result.getId()).isEqualTo("TOP-00000001");
        verify(accountRepository, never()).findById(anyString());
    }

    @Test
    void createTopup_shouldThrowUnauthorized_whenAccountNotOwned() {
        Account other = Account.builder().id("ACC-000001").userId("USR-999999")
                .currency("COP").balance(new BigDecimal("100000.00")).status("ACTIVE").build();
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(other));

        CreateTopupRequest request = CreateTopupRequest.builder()
                .accountId("ACC-000001").amount(new BigDecimal("50000.00"))
                .currency("COP").source("CARD").build();

        assertThatThrownBy(() -> topupService.createTopup(request, null, "USR-000001"))
                .isInstanceOf(UnauthorizedOperationException.class);
        verify(topupRepository, never()).save(any());
    }

    @Test
    void createTopup_shouldThrow_whenAccountNotActive() {
        Account inactive = Account.builder().id("ACC-000001").userId("USR-000001")
                .currency("COP").balance(new BigDecimal("100000.00")).status("FROZEN").build();
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(inactive));

        CreateTopupRequest request = CreateTopupRequest.builder()
                .accountId("ACC-000001").amount(new BigDecimal("50000.00"))
                .currency("COP").source("CARD").build();

        assertThatThrownBy(() -> topupService.createTopup(request, null, "USR-000001"))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void createTopup_shouldThrow_whenCurrencyMismatch() {
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(activeAccount()));

        CreateTopupRequest request = CreateTopupRequest.builder()
                .accountId("ACC-000001").amount(new BigDecimal("50000.00"))
                .currency("USD").source("CARD").build();

        assertThatThrownBy(() -> topupService.createTopup(request, null, "USR-000001"))
                .isInstanceOf(InvalidCurrencyException.class);
    }

    @Test
    void getTopupById_shouldThrowNotFound_whenNotOwner() {
        when(topupRepository.findById("TOP-00000001")).thenReturn(Optional.of(
                Topup.builder().id("TOP-00000001").userId("USR-000001").build()));

        assertThatThrownBy(() -> topupService.getTopupById("TOP-00000001", "USR-999999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTopupsByUser_shouldReturnList() {
        when(topupRepository.findByUserId("USR-000001")).thenReturn(List.of());

        assertThat(topupService.getTopupsByUser("USR-000001")).isEmpty();
    }
}

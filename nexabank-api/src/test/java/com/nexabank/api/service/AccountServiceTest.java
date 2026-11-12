package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nexabank.api.dto.account.AccountResponse;
import com.nexabank.api.dto.account.BalanceResponse;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.AccountMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, new AccountMapper());
    }

    private Account ownedAccount() {
        return Account.builder()
                .id("ACC-000001")
                .userId("USR-000001")
                .type("SAVINGS")
                .currency("COP")
                .balance(new BigDecimal("1500000.00"))
                .status("ACTIVE")
                .updatedAt(Instant.parse("2026-10-20T10:00:00Z"))
                .build();
    }

    @Test
    void getBalance_shouldReturnBalance_whenOwner() {
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(ownedAccount()));

        BalanceResponse balance = accountService.getBalance("ACC-000001", "USR-000001");

        assertThat(balance.getBalance()).isEqualTo(new BigDecimal("1500000.00"));
        assertThat(balance.getCurrency()).isEqualTo("COP");
        assertThat(balance.getUpdatedAt()).isEqualTo(Instant.parse("2026-10-20T10:00:00Z"));
    }

    @Test
    void getBalance_shouldThrow_whenAccountNotFound() {
        when(accountRepository.findById("ACC-999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getBalance("ACC-999999", "USR-000001"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getBalance_shouldThrow_whenNotOwner() {
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(ownedAccount()));

        assertThatThrownBy(() -> accountService.getBalance("ACC-000001", "USR-000002"))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void getAccountById_shouldStillWork_afterOwnershipRefactor() {
        when(accountRepository.findById("ACC-000001")).thenReturn(Optional.of(ownedAccount()));

        AccountResponse response = accountService.getAccountById("ACC-000001", "USR-000001");

        assertThat(response.getId()).isEqualTo("ACC-000001");
        assertThat(response.getBalance()).isEqualTo(new BigDecimal("1500000.00"));
    }
}

package com.nexabank.api.service;

import com.nexabank.api.dto.account.AccountResponse;
import com.nexabank.api.dto.account.BalanceResponse;
import com.nexabank.api.dto.account.CreateAccountRequest;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.AccountMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    public AccountService(AccountRepository accountRepository, AccountMapper accountMapper) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
    }

    public List<AccountResponse> getAccountsByUser(String userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    public AccountResponse getAccountById(String id, String userId) {
        Account account = getOwnedAccount(id, userId);
        return accountMapper.toResponse(account);
    }

    /**
     * Saldo de una cuenta, sin el resto del documento — usado por
     * {@code GET /api/v1/wallets/{id}/balance} para polling frecuente desde el frontend.
     */
    public BalanceResponse getBalance(String id, String userId) {
        Account account = getOwnedAccount(id, userId);
        return BalanceResponse.builder()
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    private Account getOwnedAccount(String id, String userId) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + id));

        if (userId != null && !userId.equals(account.getUserId())) {
            throw new UnauthorizedOperationException("You do not have access to this account");
        }

        return account;
    }

    public Account getAccountEntity(String id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + id));
    }

    public AccountResponse createAccount(CreateAccountRequest request, String userId) {
        Account account = accountMapper.fromCreateRequest(request, userId);
        Account saved = accountRepository.save(account);
        log.info("Created account {} for user {}", saved.getId(), userId);
        return accountMapper.toResponse(saved);
    }

    public void updateBalance(String accountId, BigDecimal newBalance) {
        accountRepository.updateBalance(accountId, newBalance);
    }
}

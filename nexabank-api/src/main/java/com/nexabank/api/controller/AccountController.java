package com.nexabank.api.controller;

import com.nexabank.api.dto.account.AccountResponse;
import com.nexabank.api.dto.account.CreateAccountRequest;
import com.nexabank.api.service.AccountService;
import com.nexabank.api.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final SecurityUtils securityUtils;

    public AccountController(AccountService accountService, SecurityUtils securityUtils) {
        this.accountService = accountService;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAccounts() {
        String userId = securityUtils.getCurrentUserId();
        List<AccountResponse> accounts = accountService.getAccountsByUser(userId);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        AccountResponse account = accountService.getAccountById(id, userId);
        return ResponseEntity.ok(account);
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        String userId = securityUtils.getCurrentUserId();
        AccountResponse created = accountService.createAccount(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}

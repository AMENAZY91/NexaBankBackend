package com.nexabank.api.controller;

import com.nexabank.api.dto.account.AccountResponse;
import com.nexabank.api.dto.account.BalanceResponse;
import com.nexabank.api.dto.account.CreateAccountRequest;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Alias de recurso para {@code /api/v1/accounts} bajo el nombre "wallet", que es el
 * término que espera el contrato del frontend (ver docs/API_AUDIT.md, sección "Gaps
 * frente al frontend"). Delega íntegramente en {@link AccountService} — no introduce
 * un dominio paralelo ni una colección Firestore nueva. {@code /api/v1/accounts} se
 * mantiene sin cambios para no romper otros consumidores existentes.
 */
@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final AccountService accountService;
    private final SecurityUtils securityUtils;

    public WalletController(AccountService accountService, SecurityUtils securityUtils) {
        this.accountService = accountService;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getWallets() {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(accountService.getAccountsByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getWallet(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(accountService.getAccountById(id, userId));
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getWalletBalance(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(accountService.getBalance(id, userId));
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createWallet(@Valid @RequestBody CreateAccountRequest request) {
        String userId = securityUtils.getCurrentUserId();
        AccountResponse created = accountService.createAccount(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}

package com.nexabank.api.controller;

import com.nexabank.api.dto.transaction.CreateTransactionRequest;
import com.nexabank.api.dto.transaction.TransactionResponse;
import com.nexabank.api.dto.transaction.TransactionFilterRequest;
import com.nexabank.api.service.TransactionService;
import com.nexabank.api.service.AccountService;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.dto.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final SecurityUtils securityUtils;

    public TransactionController(TransactionService transactionService,
                                  AccountService accountService,
                                  SecurityUtils securityUtils) {
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TransactionResponse>> getTransactions(
            @RequestParam(value = "accountId", required = false) String accountId,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "cursor", required = false) String cursor) {
        if (accountId == null || accountId.isBlank()) {
            userId = securityUtils.getCurrentUserId();
        }

        // Paginación por cursor real de Firestore: se activa si el cliente envía
        // el parámetro `cursor` (incluso vacío, para pedir la primera página por cursor).
        // Si no se envía, se mantiene el comportamiento histórico page/size en memoria.
        if (cursor != null) {
            TransactionService.TransactionCursorPage cursorPage =
                    transactionService.getTransactionsByCursor(accountId, userId, cursor.isBlank() ? null : cursor, size);
            PageResponse<TransactionResponse> pageResponse = PageResponse.<TransactionResponse>builder()
                    .data(cursorPage.items())
                    .page(0)
                    .size(size)
                    .totalElements(cursorPage.items().size())
                    .totalPages(cursorPage.nextCursor() != null ? -1 : 1)
                    .nextCursor(cursorPage.nextCursor())
                    .build();
            return ResponseEntity.ok(pageResponse);
        }

        List<TransactionResponse> transactions = transactionService.getTransactions(accountId, userId);
        PageResponse<TransactionResponse> pageResponse = PageResponse.of(transactions, page, size);
        return ResponseEntity.ok(pageResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        TransactionResponse transaction = transactionService.getTransactionById(id, userId);
        return ResponseEntity.ok(transaction);
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        String userId = securityUtils.getCurrentUserId();
        TransactionResponse created = transactionService.createTransaction(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}

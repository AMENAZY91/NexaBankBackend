package com.nexabank.api.mapper;

import com.nexabank.api.dto.transaction.CreateTransactionRequest;
import com.nexabank.api.dto.transaction.TransactionResponse;
import com.nexabank.api.model.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mapper para transformaciones Transaction ↔ DTO.
 *
 * <p>Los DTOs de respuesta están alineados 1:1 con el frontend
 * (interface TransactionListProps):
 * <pre>
 * { id, name, note, amount, incoming, color }
 * </pre>
 */
@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        boolean incoming = "INCOME".equalsIgnoreCase(transaction.getType())
                || "TRANSFER_IN".equalsIgnoreCase(transaction.getType());
        String color = incoming ? "green" :
                ("EXPENSE".equalsIgnoreCase(transaction.getType()) ? "rose" : "amber");

        String name = transaction.getDescription();
        if (name == null || name.isBlank()) {
            name = incoming ? "Ingreso" : "Egreso";
        }
        String note = transaction.getCategory() != null ? transaction.getCategory() : "";

        return TransactionResponse.builder()
                .id(transaction.getId())
                .name(name)
                .note(note)
                .amount(transaction.getAmount())
                .incoming(incoming)
                .color(color)
                .build();
    }

    public Transaction fromCreateRequest(CreateTransactionRequest request, String userId) {
        Instant now = Instant.now();
        return Transaction.builder()
                .accountId(request.getAccountId())
                .userId(userId)
                .type(request.getType())
                .category(request.getCategory())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .status("PENDING")
                .timestamp(now)
                .createdAt(now)
                .build();
    }
}
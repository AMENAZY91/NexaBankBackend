package com.nexabank.api.service;

import com.nexabank.api.dto.card.CardResponse;
import com.nexabank.api.dto.card.CreateCardRequest;
import com.nexabank.api.dto.card.UpdateCardRequest;
import com.nexabank.api.exception.InvalidTransactionException;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.mapper.CardMapper;
import com.nexabank.api.model.Account;
import com.nexabank.api.model.AuditLog;
import com.nexabank.api.model.Card;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.CardRepository;
import com.nexabank.api.service.provider.CardIssuer;
import com.nexabank.api.service.provider.CardIssuanceRequest;
import com.nexabank.api.service.provider.CardLimits;
import com.nexabank.api.service.provider.IssuedCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Servicio de tarjetas.
 *
 * <p>Compone sobre {@link CardIssuer} (mock hoy) para la emisión/bloqueo/límites en
 * sí. A diferencia de {@code PaymentRequest}, una tarjeta tiene una sola parte
 * legítima (el titular) — por eso {@link #getOwnedCard} siempre devuelve 404 (no 403)
 * a cualquiera que no sea el dueño, sin la capa adicional de roles que sí hacía falta
 * en solicitudes de pago.
 */
@Service
public class CardService {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final CardIssuer cardIssuer;
    private final NotificationService notificationService;
    private final AuditLogRepository auditLogRepository;
    private final CardMapper cardMapper;

    @Value("${app.card.default-daily-limit:2000000}")
    private BigDecimal defaultDailyLimit;

    @Value("${app.card.default-monthly-limit:20000000}")
    private BigDecimal defaultMonthlyLimit;

    public CardService(CardRepository cardRepository,
                        AccountRepository accountRepository,
                        CardIssuer cardIssuer,
                        NotificationService notificationService,
                        AuditLogRepository auditLogRepository,
                        CardMapper cardMapper) {
        this.cardRepository = cardRepository;
        this.accountRepository = accountRepository;
        this.cardIssuer = cardIssuer;
        this.notificationService = notificationService;
        this.auditLogRepository = auditLogRepository;
        this.cardMapper = cardMapper;
    }

    public List<CardResponse> getCardsByUser(String userId) {
        return cardRepository.findByUserId(userId).stream().map(cardMapper::toResponse).toList();
    }

    public CardResponse getCardById(String id, String userId) {
        return cardMapper.toResponse(getOwnedCard(id, userId));
    }

    public CardResponse issue(CreateCardRequest request, String userId) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.getAccountId()));
        if (!account.getUserId().equals(userId)) {
            throw new com.nexabank.api.exception.UnauthorizedOperationException(
                    "The account does not belong to the authenticated user");
        }
        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new InvalidTransactionException("Account is not active");
        }

        IssuedCard issued = cardIssuer.issue(new CardIssuanceRequest(userId, account.getId()));

        Card card = Card.builder()
                .userId(userId)
                .accountId(account.getId())
                .brand(issued.brand())
                .last4(issued.last4())
                .expiryMonth(issued.expiryMonth())
                .expiryYear(issued.expiryYear())
                .issuerToken(issued.token())
                .status("ACTIVE")
                .dailyLimit(request.getDailyLimit() != null ? request.getDailyLimit() : defaultDailyLimit)
                .monthlyLimit(request.getMonthlyLimit() != null ? request.getMonthlyLimit() : defaultMonthlyLimit)
                .createdAt(Instant.now())
                .build();
        Card saved = cardRepository.save(card);

        audit(userId, "CARD_ISSUED", saved);
        notificationService.notify(userId, "CARD_ISSUED", "Tarjeta emitida",
                "Se emitió una tarjeta terminada en " + saved.getLast4(), "CARD", saved.getId());

        log.info("Card {} issued for user {} (account {})", saved.getId(), userId, account.getId());

        return cardMapper.toResponse(saved);
    }

    public CardResponse update(String id, UpdateCardRequest request, String userId) {
        Card card = getOwnedCard(id, userId);

        if ("CANCELLED".equalsIgnoreCase(card.getStatus())) {
            throw new InvalidTransactionException("Cancelled cards cannot be modified");
        }

        Card.CardBuilder updated = card.toBuilder();
        boolean changed = false;

        if (request.getStatus() != null) {
            applyStatusChange(card, request.getStatus());
            updated.status(request.getStatus());
            changed = true;
        }
        if (request.getDailyLimit() != null || request.getMonthlyLimit() != null) {
            BigDecimal daily = request.getDailyLimit() != null ? request.getDailyLimit() : card.getDailyLimit();
            BigDecimal monthly = request.getMonthlyLimit() != null ? request.getMonthlyLimit() : card.getMonthlyLimit();
            cardIssuer.setLimits(card.getIssuerToken(), new CardLimits(daily, monthly));
            updated.dailyLimit(daily).monthlyLimit(monthly);
            audit(userId, "CARD_LIMITS_UPDATED", card);
            changed = true;
        }

        if (!changed) {
            return cardMapper.toResponse(card);
        }

        Card saved = cardRepository.save(updated.build());
        return cardMapper.toResponse(saved);
    }

    public void cancel(String id, String userId) {
        Card card = getOwnedCard(id, userId);
        if ("CANCELLED".equalsIgnoreCase(card.getStatus())) {
            throw new InvalidTransactionException("Card is already cancelled");
        }

        Card cancelled = card.toBuilder().status("CANCELLED").build();
        cardRepository.save(cancelled);

        audit(userId, "CARD_CANCELLED", card);
        notificationService.notify(userId, "CARD_CANCELLED", "Tarjeta cancelada",
                "La tarjeta terminada en " + card.getLast4() + " fue cancelada", "CARD", card.getId());
    }

    private void applyStatusChange(Card card, String newStatus) {
        if (newStatus.equalsIgnoreCase(card.getStatus())) {
            return;
        }
        if ("BLOCKED".equalsIgnoreCase(newStatus)) {
            cardIssuer.block(card.getIssuerToken());
            audit(card.getUserId(), "CARD_BLOCKED", card);
        } else if ("ACTIVE".equalsIgnoreCase(newStatus)) {
            cardIssuer.unblock(card.getIssuerToken());
            audit(card.getUserId(), "CARD_UNBLOCKED", card);
        }
    }

    private Card getOwnedCard(String id, String userId) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with ID: " + id));

        if (userId != null && !userId.equals(card.getUserId())) {
            throw new ResourceNotFoundException("Card not found with ID: " + id);
        }

        return card;
    }

    private void audit(String userId, String action, Card card) {
        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action(action)
                .resourceType("CARD")
                .resourceId(card.getId())
                .status("SUCCESS")
                .timestamp(Instant.now())
                .metadata(Map.of("last4", card.getLast4() != null ? card.getLast4() : "----"))
                .build());
    }
}

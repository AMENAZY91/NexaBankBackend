package com.nexabank.api.controller;

import com.nexabank.api.dto.card.CardResponse;
import com.nexabank.api.dto.card.CreateCardRequest;
import com.nexabank.api.dto.card.UpdateCardRequest;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.CardService;
import com.nexabank.api.service.ConfirmationTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de tarjetas.
 *
 * <p>Rutas:
 * <ul>
 *   <li>GET /api/v1/cards — listar tarjetas del usuario</li>
 *   <li>GET /api/v1/cards/{id} — obtener tarjeta</li>
 *   <li>POST /api/v1/cards — emitir tarjeta (con X-Confirmation-Token)</li>
 *   <li>PATCH /api/v1/cards/{id} — bloquear/desbloquear y/o actualizar límites</li>
 *   <li>DELETE /api/v1/cards/{id} — cancelar (con X-Confirmation-Token)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/cards")
public class CardController {

    private final CardService cardService;
    private final SecurityUtils securityUtils;
    private final ConfirmationTokenService confirmationTokenService;

    public CardController(CardService cardService, SecurityUtils securityUtils,
                           ConfirmationTokenService confirmationTokenService) {
        this.cardService = cardService;
        this.securityUtils = securityUtils;
        this.confirmationTokenService = confirmationTokenService;
    }

    @GetMapping
    public ResponseEntity<List<CardResponse>> getCards() {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(cardService.getCardsByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CardResponse> getCard(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(cardService.getCardById(id, userId));
    }

    @PostMapping
    public ResponseEntity<CardResponse> issueCard(
            @Valid @RequestBody CreateCardRequest request,
            @RequestHeader(value = "X-Confirmation-Token", required = false) String confirmationToken) {
        String userId = securityUtils.getCurrentUserId();
        confirmationTokenService.validateAndConsume(confirmationToken, userId);
        CardResponse created = cardService.issue(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CardResponse> updateCard(@PathVariable("id") String id,
                                                     @Valid @RequestBody UpdateCardRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(cardService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelCard(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-Confirmation-Token", required = false) String confirmationToken) {
        String userId = securityUtils.getCurrentUserId();
        confirmationTokenService.validateAndConsume(confirmationToken, userId);
        cardService.cancel(id, userId);
        return ResponseEntity.noContent().build();
    }
}

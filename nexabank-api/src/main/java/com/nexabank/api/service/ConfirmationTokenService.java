package com.nexabank.api.service;

import com.nexabank.api.exception.InvalidConfirmationTokenException;
import com.nexabank.api.model.ConfirmationToken;
import com.nexabank.api.repository.ConfirmationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de tokens de confirmación de corta vida (mejora #22).
 *
 * <p>Emitidos tras un {@code pin/verify} exitoso; requeridos en el header
 * {@code X-Confirmation-Token} para operaciones de mutación de dinero. Vida útil: 5 minutos.
 * Single-use: se marcan como usados al validarse exitosamente y no pueden reutilizarse.
 */
@Service
public class ConfirmationTokenService {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationTokenService.class);
    private static final long TOKEN_LIFETIME_MINUTES = 5;

    private final ConfirmationTokenRepository confirmationTokenRepository;

    public ConfirmationTokenService(ConfirmationTokenRepository confirmationTokenRepository) {
        this.confirmationTokenRepository = confirmationTokenRepository;
    }

    /**
     * Genera y persiste un nuevo token de confirmación para el usuario dado.
     */
    public String generate(String userId) {
        Instant now = Instant.now();
        String token = UUID.randomUUID().toString();
        confirmationTokenRepository.save(ConfirmationToken.builder()
                .token(token)
                .userId(userId)
                .expiresAt(now.plus(TOKEN_LIFETIME_MINUTES, ChronoUnit.MINUTES))
                .used(false)
                .createdAt(now)
                .build());
        return token;
    }

    /**
     * Valida un token de confirmación para el usuario autenticado y, si es válido, lo marca
     * como usado (single-use) antes de retornar.
     *
     * @throws InvalidConfirmationTokenException si el token falta, no existe, no pertenece al
     *                                            usuario, está expirado o ya fue usado
     */
    public void validateAndConsume(String token, String userId) {
        if (token == null || token.isBlank()) {
            throw new InvalidConfirmationTokenException("Missing X-Confirmation-Token header");
        }

        Optional<ConfirmationToken> found = confirmationTokenRepository.findByToken(token);
        ConfirmationToken ct = found.orElseThrow(
                () -> new InvalidConfirmationTokenException("Invalid confirmation token"));

        if (!ct.getUserId().equals(userId)) {
            throw new InvalidConfirmationTokenException("Confirmation token does not belong to the authenticated user");
        }
        if (ct.isUsed()) {
            throw new InvalidConfirmationTokenException("Confirmation token has already been used");
        }
        if (ct.getExpiresAt() == null || ct.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidConfirmationTokenException("Confirmation token has expired");
        }

        confirmationTokenRepository.markUsed(token);
        log.debug("Confirmation token consumed for user: {}", userId);
    }
}

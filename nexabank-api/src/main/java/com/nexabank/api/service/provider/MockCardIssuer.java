package com.nexabank.api.service.provider;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Adaptador mock de {@link CardIssuer} — única implementación hoy. Genera
 * {@code last4} directamente como 4 dígitos aleatorios (nunca a partir de un número de
 * tarjeta completo que luego se trunca) y un {@code token} opaco — en ningún momento
 * existe en memoria un PAN/CVV real o realista.
 *
 * <p>{@code block}/{@code unblock}/{@code setLimits} son no-op: no hay ningún
 * procesador externo real al que avisar todavía. Existen para completar el contrato
 * del puerto de cara a un emisor real futuro.
 */
@Component
public class MockCardIssuer implements CardIssuer {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BRAND = "VIRTUAL";
    private static final int VALIDITY_YEARS = 3;

    @Override
    public IssuedCard issue(CardIssuanceRequest request) {
        String last4 = String.format("%04d", RANDOM.nextInt(10_000));
        String token = UUID.randomUUID().toString();
        YearMonth expiry = YearMonth.now().plusYears(VALIDITY_YEARS);
        return new IssuedCard(token, BRAND, last4, expiry.getMonthValue(), expiry.getYear());
    }

    @Override
    public void block(String cardToken) {
        // no-op: sin procesador externo real todavía.
    }

    @Override
    public void unblock(String cardToken) {
        // no-op: sin procesador externo real todavía.
    }

    @Override
    public void setLimits(String cardToken, CardLimits limits) {
        // no-op: sin procesador externo real todavía.
    }
}

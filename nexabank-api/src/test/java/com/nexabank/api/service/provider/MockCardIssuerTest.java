package com.nexabank.api.service.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MockCardIssuerTest {

    private final MockCardIssuer issuer = new MockCardIssuer();

    @Test
    void issue_shouldReturnLast4ExactlyFourDigits_neverAFullCardNumber() {
        IssuedCard issued = issuer.issue(new CardIssuanceRequest("USR-000001", "ACC-000001"));

        assertThat(issued.last4()).hasSize(4);
        assertThat(issued.last4()).matches("\\d{4}");
    }

    @Test
    void issue_shouldReturnOpaqueToken_notResemblingACardNumber() {
        IssuedCard issued = issuer.issue(new CardIssuanceRequest("USR-000001", "ACC-000001"));

        // Un UUID no es un número de tarjeta válido (no son solo dígitos, tiene guiones/letras).
        assertThat(issued.token()).isNotBlank();
        assertThat(issued.token()).doesNotMatch("\\d{12,19}");
    }

    @Test
    void issue_shouldReturnFutureExpiry() {
        IssuedCard issued = issuer.issue(new CardIssuanceRequest("USR-000001", "ACC-000001"));

        assertThat(issued.expiryYear()).isGreaterThan(java.time.Year.now().getValue());
        assertThat(issued.expiryMonth()).isBetween(1, 12);
    }

    @Test
    void blockUnblockSetLimits_shouldNotThrow_evenWithoutRealIssuer() {
        issuer.block("any-token");
        issuer.unblock("any-token");
        issuer.setLimits("any-token", new CardLimits(BigDecimal.TEN, BigDecimal.valueOf(100)));
        // no-op: solo verifica que no lanza excepción.
    }
}

package com.nexabank.api.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Verifica el mecanismo de persistencia de dinero elegido para la mejora #16: guardar
 * {@code BigDecimal.toPlainString()} (String) en Firestore en vez de {@code .doubleValue()}
 * (double IEEE-754), y leer con {@code new BigDecimal(String)} en vez de {@code doc.getDouble(...)}.
 *
 * <p>Esta prueba no requiere un emulador de Firestore: ejercita directamente el mismo par de
 * conversiones que usan {@code AccountRepository}, {@code TransactionRepository} y
 * {@code TransferRepository} (ver sus métodos {@code *ToMap}/{@code document*} y
 * {@code readBalance}/{@code readAmount}), demostrando que el round-trip es exacto.
 */
class MoneyPrecisionTest {

    @Test
    void stringRoundTrip_shouldPreserveExactValue_forTypicalAmount() {
        BigDecimal original = new BigDecimal("10.10");

        String persisted = original.toPlainString();
        BigDecimal reread = new BigDecimal(persisted);

        assertThat(persisted).isEqualTo("10.10");
        assertThat(reread).isEqualByComparingTo(original);
    }

    @Test
    void stringRoundTrip_shouldPreserveExactValue_afterRepeatedAdditions() {
        // Caso clásico de imprecisión IEEE-754: 0.1 + 0.2 != 0.3 exacto en double.
        BigDecimal a = new BigDecimal("0.10");
        BigDecimal b = new BigDecimal("0.20");
        BigDecimal sum = a.add(b);

        String persisted = sum.toPlainString();
        BigDecimal reread = new BigDecimal(persisted);

        assertThat(reread).isEqualByComparingTo(new BigDecimal("0.30"));
    }

    @Test
    void doubleFormat_losesExactDecimalRepresentation_demonstratingTheBugBeingFixed() {
        // Demuestra el bug real que la mejora #16 corrige: 0.1 + 0.2 en double NO es
        // exactamente 0.3 (es 0.30000000000000004), por la representación binaria IEEE-754.
        // El formato String (BigDecimal.toPlainString()) nunca pasa por esta conversión.
        double sumAsDouble = 0.1d + 0.2d;
        assertThat(sumAsDouble).isNotEqualTo(0.3d);

        // Con BigDecimal + String, la misma suma es exacta:
        BigDecimal sumAsBigDecimal = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        assertThat(sumAsBigDecimal.toPlainString()).isEqualTo("0.3");
    }
}

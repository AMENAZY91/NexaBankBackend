package com.nexabank.api.dto.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verificación explícita (mismo espíritu que {@code MoneyPrecisionTest}) de que
 * ningún DTO de cara al cliente puede llegar a exponer un número de tarjeta completo
 * — ni siquiera por accidente al agregar un campo nuevo en el futuro. No hay ningún
 * campo llamado "pan"/"cardNumber"/"cvv"/"cvc" en {@link CardResponse}, y "last4" es,
 * por definición de nombre y uso, el único dato de tarjeta parcial que se devuelve.
 */
class CardNumberExposureTest {

    private static final List<String> FORBIDDEN_FIELD_SUBSTRINGS = List.of("pan", "cardnumber", "cvv", "cvc");

    @Test
    void cardResponse_shouldNotDeclareAnyFullCardNumberOrCvvField() {
        List<String> fieldNames = fieldNamesOf(CardResponse.class);

        for (String forbidden : FORBIDDEN_FIELD_SUBSTRINGS) {
            assertThat(fieldNames)
                    .as("CardResponse no debe declarar un campo que contenga '%s'", forbidden)
                    .noneMatch(name -> name.toLowerCase().contains(forbidden));
        }
    }

    @Test
    void cardResponse_shouldOnlyExposeLast4_notIssuerToken() {
        List<String> fieldNames = fieldNamesOf(CardResponse.class);

        assertThat(fieldNames).contains("last4");
        assertThat(fieldNames).doesNotContain("issuerToken", "token");
    }

    private List<String> fieldNamesOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }
}

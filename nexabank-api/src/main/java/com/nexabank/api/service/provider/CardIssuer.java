package com.nexabank.api.service.provider;

/**
 * Puerto hacia un emisor externo de tarjetas (ej. Stripe Issuing). Hoy la única
 * implementación es {@link MockCardIssuer}. El día que se conecte un emisor real, se
 * agrega una nueva implementación de esta interfaz — {@link com.nexabank.api.service.CardService}
 * no necesita cambiar.
 *
 * <p>Ninguna implementación de este puerto debe exponer o persistir un PAN/CVV
 * completo, ni siquiera el mock — solo {@code last4} y un {@code token} opaco de
 * correlación (ver {@link IssuedCard}).
 */
public interface CardIssuer {

    IssuedCard issue(CardIssuanceRequest request);

    void block(String cardToken);

    void unblock(String cardToken);

    void setLimits(String cardToken, CardLimits limits);
}

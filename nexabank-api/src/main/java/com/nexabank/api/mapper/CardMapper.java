package com.nexabank.api.mapper;

import com.nexabank.api.dto.card.CardResponse;
import com.nexabank.api.model.Card;
import org.springframework.stereotype.Component;

/**
 * Mapper para transformaciones Card → DTO. No mapea {@code issuerToken} — ver
 * {@link CardResponse}.
 */
@Component
public class CardMapper {

    public CardResponse toResponse(Card card) {
        return CardResponse.builder()
                .id(card.getId())
                .accountId(card.getAccountId())
                .brand(card.getBrand())
                .last4(card.getLast4())
                .expiryMonth(card.getExpiryMonth())
                .expiryYear(card.getExpiryYear())
                .status(card.getStatus())
                .dailyLimit(card.getDailyLimit())
                .monthlyLimit(card.getMonthlyLimit())
                .createdAt(card.getCreatedAt())
                .build();
    }
}

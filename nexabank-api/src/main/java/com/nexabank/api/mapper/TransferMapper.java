package com.nexabank.api.mapper;

import com.nexabank.api.dto.transfer.TransferResponse;
import com.nexabank.api.model.Transfer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Mapper para transformaciones Transfer ↔ DTO.
 */
@Component
public class TransferMapper {

    public TransferResponse toResponse(Transfer transfer) {
        return TransferResponse.builder()
                .id(transfer.getId())
                .sourceAccountId(transfer.getSourceAccountId())
                .destinationAccountId(transfer.getDestinationAccountId())
                .sourceUserId(transfer.getSourceUserId())
                .destinationUserId(transfer.getDestinationUserId())
                .amount(transfer.getAmount())
                .sourceCurrency(transfer.getSourceCurrency())
                .destinationCurrency(transfer.getDestinationCurrency())
                .destinationAmount(transfer.getDestinationAmount())
                .currency(transfer.getSourceCurrency())
                .fee(transfer.getFee())
                .fxRate(transfer.getFxRate())
                .fxMargin(transfer.getFxMargin())
                .status(transfer.getStatus())
                .description(transfer.getDescription())
                .trackingNumber(transfer.getTrackingNumber())
                .estimatedDeliveryDate(transfer.getEstimatedDeliveryDate())
                .createdAt(transfer.getCreatedAt())
                .completedAt(transfer.getCompletedAt())
                .recipientInfo(transfer.getRecipientInfo())
                .senderInfo(transfer.getSenderInfo())
                .build();
    }
}

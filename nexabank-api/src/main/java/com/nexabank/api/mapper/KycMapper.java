package com.nexabank.api.mapper;

import com.nexabank.api.dto.kyc.KycProfileResponse;
import com.nexabank.api.model.KycProfile;
import org.springframework.stereotype.Component;

/**
 * Mapper para transformaciones KycProfile → DTO.
 */
@Component
public class KycMapper {

    public KycProfileResponse toResponse(KycProfile profile) {
        return KycProfileResponse.builder()
                .userId(profile.getUserId())
                .status(profile.getStatus())
                .documentType(profile.getDocumentType())
                .documentNumber(profile.getDocumentNumber())
                .fullName(profile.getFullName())
                .dateOfBirth(profile.getDateOfBirth())
                .address(profile.getAddress())
                .submittedAt(profile.getSubmittedAt())
                .reviewedAt(profile.getReviewedAt())
                .rejectionReason(profile.getRejectionReason())
                .build();
    }
}

package com.nexabank.api.service;

import com.nexabank.api.dto.beneficiary.BeneficiaryResponse;
import com.nexabank.api.dto.beneficiary.CreateBeneficiaryRequest;
import com.nexabank.api.dto.beneficiary.UpdateBeneficiaryRequest;
import com.nexabank.api.exception.ResourceNotFoundException;
import com.nexabank.api.exception.UnauthorizedOperationException;
import com.nexabank.api.mapper.BeneficiaryMapper;
import com.nexabank.api.model.Beneficiary;
import com.nexabank.api.repository.BeneficiaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio de beneficiarios con soporte para datos internacionales.
 */
@Service
public class BeneficiaryService {

    private static final Logger log = LoggerFactory.getLogger(BeneficiaryService.class);

    private final BeneficiaryRepository beneficiaryRepository;
    private final BeneficiaryMapper beneficiaryMapper;

    public BeneficiaryService(BeneficiaryRepository beneficiaryRepository,
                              BeneficiaryMapper beneficiaryMapper) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.beneficiaryMapper = beneficiaryMapper;
    }

    public List<BeneficiaryResponse> getBeneficiariesByUser(String userId) {
        return beneficiaryRepository.findByUserId(userId).stream()
                .map(beneficiaryMapper::toResponse)
                .toList();
    }

    public BeneficiaryResponse getBeneficiaryById(String id, String userId) {
        Beneficiary beneficiary = beneficiaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found with ID: " + id));

        if (userId != null && !userId.equals(beneficiary.getUserId())) {
            throw new UnauthorizedOperationException("You do not have access to this beneficiary");
        }

        return beneficiaryMapper.toResponse(beneficiary);
    }

    public BeneficiaryResponse createBeneficiary(CreateBeneficiaryRequest request, String userId) {
        Beneficiary beneficiary = beneficiaryMapper.fromCreateRequest(request, userId);
        Beneficiary saved = beneficiaryRepository.save(beneficiary);
        log.info("Created beneficiary {} for user {}", saved.getId(), userId);
        return beneficiaryMapper.toResponse(saved);
    }

    public BeneficiaryResponse updateBeneficiary(String id, UpdateBeneficiaryRequest request, String userId) {
        Beneficiary beneficiary = beneficiaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found with ID: " + id));

        if (userId != null && !userId.equals(beneficiary.getUserId())) {
            throw new UnauthorizedOperationException("You do not have access to this beneficiary");
        }

        beneficiaryMapper.updateFromRequest(request, beneficiary);
        Beneficiary updated = beneficiaryRepository.save(beneficiary);
        return beneficiaryMapper.toResponse(updated);
    }

    public void deleteBeneficiary(String id, String userId) {
        Beneficiary beneficiary = beneficiaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found with ID: " + id));

        if (userId != null && !userId.equals(beneficiary.getUserId())) {
            throw new UnauthorizedOperationException("You do not have access to this beneficiary");
        }

        beneficiaryRepository.delete(id);
        log.info("Deleted beneficiary {}", id);
    }
}

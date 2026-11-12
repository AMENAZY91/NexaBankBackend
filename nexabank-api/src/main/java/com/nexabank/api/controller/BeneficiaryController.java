package com.nexabank.api.controller;

import com.nexabank.api.dto.beneficiary.*;
import com.nexabank.api.service.BeneficiaryService;
import com.nexabank.api.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;
    private final SecurityUtils securityUtils;

    public BeneficiaryController(BeneficiaryService beneficiaryService, SecurityUtils securityUtils) {
        this.beneficiaryService = beneficiaryService;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public ResponseEntity<List<BeneficiaryResponse>> getBeneficiaries() {
        String userId = securityUtils.getCurrentUserId();
        List<BeneficiaryResponse> beneficiaries = beneficiaryService.getBeneficiariesByUser(userId);
        return ResponseEntity.ok(beneficiaries);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BeneficiaryResponse> getBeneficiary(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        BeneficiaryResponse beneficiary = beneficiaryService.getBeneficiaryById(id, userId);
        return ResponseEntity.ok(beneficiary);
    }

    @PostMapping
    public ResponseEntity<BeneficiaryResponse> createBeneficiary(@Valid @RequestBody CreateBeneficiaryRequest request) {
        String userId = securityUtils.getCurrentUserId();
        BeneficiaryResponse created = beneficiaryService.createBeneficiary(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BeneficiaryResponse> updateBeneficiary(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateBeneficiaryRequest request) {
        String userId = securityUtils.getCurrentUserId();
        BeneficiaryResponse updated = beneficiaryService.updateBeneficiary(id, request, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBeneficiary(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        beneficiaryService.deleteBeneficiary(id, userId);
        return ResponseEntity.noContent().build();
    }
}

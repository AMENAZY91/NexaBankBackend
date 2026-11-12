package com.nexabank.api.controller;

import com.nexabank.api.dto.beneficiary.BeneficiaryResponse;
import com.nexabank.api.dto.beneficiary.CreateBeneficiaryRequest;
import com.nexabank.api.dto.beneficiary.UpdateBeneficiaryRequest;
import com.nexabank.api.security.SecurityUtils;
import com.nexabank.api.service.BeneficiaryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Alias de recurso para {@code /api/v1/beneficiaries} bajo el nombre "contact", que es
 * el término que espera el contrato del frontend (ver docs/API_AUDIT.md, sección "Gaps
 * frente al frontend"). Delega íntegramente en {@link BeneficiaryService} — no introduce
 * un dominio paralelo ni una colección Firestore nueva. {@code /api/v1/beneficiaries} se
 * mantiene sin cambios para no romper otros consumidores existentes.
 */
@RestController
@RequestMapping("/api/v1/contacts")
public class ContactController {

    private final BeneficiaryService beneficiaryService;
    private final SecurityUtils securityUtils;

    public ContactController(BeneficiaryService beneficiaryService, SecurityUtils securityUtils) {
        this.beneficiaryService = beneficiaryService;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public ResponseEntity<List<BeneficiaryResponse>> getContacts() {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(beneficiaryService.getBeneficiariesByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BeneficiaryResponse> getContact(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(beneficiaryService.getBeneficiaryById(id, userId));
    }

    @PostMapping
    public ResponseEntity<BeneficiaryResponse> createContact(@Valid @RequestBody CreateBeneficiaryRequest request) {
        String userId = securityUtils.getCurrentUserId();
        BeneficiaryResponse created = beneficiaryService.createBeneficiary(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BeneficiaryResponse> updateContact(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateBeneficiaryRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(beneficiaryService.updateBeneficiary(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable("id") String id) {
        String userId = securityUtils.getCurrentUserId();
        beneficiaryService.deleteBeneficiary(id, userId);
        return ResponseEntity.noContent().build();
    }
}

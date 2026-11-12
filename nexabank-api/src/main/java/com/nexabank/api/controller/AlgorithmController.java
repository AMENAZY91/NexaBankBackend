package com.nexabank.api.controller;

import com.nexabank.api.dto.algorithm.SearchRequest;
import com.nexabank.api.dto.algorithm.SearchResponse;
import com.nexabank.api.dto.algorithm.SortRequest;
import com.nexabank.api.dto.algorithm.SortResponse;
import com.nexabank.api.service.AlgorithmService;
import com.nexabank.api.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/algorithms")
public class AlgorithmController {

    private final AlgorithmService algorithmService;
    private final SecurityUtils securityUtils;

    public AlgorithmController(AlgorithmService algorithmService,
                                SecurityUtils securityUtils) {
        this.algorithmService = algorithmService;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/sort")
    public ResponseEntity<SortResponse> executeSort(@RequestBody SortRequest request) {
        String userId = securityUtils.getCurrentUserId();
        String accountId = null;
        SortResponse result = algorithmService.executeSort(request, accountId, userId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> executeSearch(@RequestBody SearchRequest request) {
        String userId = securityUtils.getCurrentUserId();
        String accountId = null;
        SearchResponse result = algorithmService.executeSearch(request, accountId, userId);
        return ResponseEntity.ok(result);
    }
}

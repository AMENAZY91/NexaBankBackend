package com.nexabank.api.controller;

import com.nexabank.api.service.DataPurgeService;
import com.nexabank.api.service.DataSeederService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/seed")
public class SeedController {

    private final DataSeederService dataSeederService;
    private final DataPurgeService dataPurgeService;

    public SeedController(DataSeederService dataSeederService,
                          DataPurgeService dataPurgeService) {
        this.dataSeederService = dataSeederService;
        this.dataPurgeService = dataPurgeService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> seedDatabase() {
        return ResponseEntity.ok(dataSeederService.seedDatabase());
    }

    @DeleteMapping("/purge")
    public ResponseEntity<Map<String, Object>> purgeDatabase() {
        return ResponseEntity.ok(dataPurgeService.purgeDatabase());
    }
}

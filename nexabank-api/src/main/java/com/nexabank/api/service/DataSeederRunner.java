package com.nexabank.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

// Deshabilitado: los datos se crean mediante endpoints controlados, no al iniciar.
// @Component
public class DataSeederRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeederRunner.class);

    private final DataSeederService dataSeederService;

    public DataSeederRunner(DataSeederService dataSeederService) {
        this.dataSeederService = dataSeederService;
    }

    @Override
    public void run(String... args) {
        log.info("DataSeederRunner is disabled. Skipping automatic seeding.");
    }
}

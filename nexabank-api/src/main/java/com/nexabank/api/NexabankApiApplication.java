package com.nexabank.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} habilita {@code PaymentRequestService.expireOverduePendingRequests}
 * (barrido periódico de solicitudes de pago vencidas — ver docs/IMPLEMENTATION_PLAN.md, Fase 5).
 */
@SpringBootApplication
@EnableScheduling
public class NexabankApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexabankApiApplication.class, args);
    }
}

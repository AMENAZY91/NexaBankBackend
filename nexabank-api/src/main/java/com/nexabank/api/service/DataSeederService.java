package com.nexabank.api.service;

import com.nexabank.api.model.*;
import com.nexabank.api.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class DataSeederService {

    private static final Logger log = LoggerFactory.getLogger(DataSeederService.class);

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkService benchmarkService;

    public DataSeederService(UserRepository userRepository,
                             AccountRepository accountRepository,
                             TransactionRepository transactionRepository,
                             BeneficiaryRepository beneficiaryRepository,
                             BenchmarkRepository benchmarkRepository,
                             BenchmarkService benchmarkService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkService = benchmarkService;
    }

    public Map<String, Object> seedDatabase() {
        log.info("Starting database seeding process...");
        Instant now = Instant.now();

        // 1. Usuarios
        User user1 = userRepository.save(User.builder()
                .id("USR-000001")
                .email("juan.restr@nexabank.com")
                .displayName("Juan Restrepo")
                .phone("+573001234567")
                .status("ACTIVE")
                .roles(List.of("USER", "ADMIN"))
                .createdAt(now.minus(30, ChronoUnit.DAYS))
                .updatedAt(now)
                .lastLoginAt(now)
                .build());

        User user2 = userRepository.save(User.builder()
                .id("USR-000002")
                .email("maria.gomez@nexabank.com")
                .displayName("Maria Gomez")
                .phone("+573109876543")
                .status("ACTIVE")
                .roles(List.of("USER"))
                .createdAt(now.minus(20, ChronoUnit.DAYS))
                .updatedAt(now)
                .lastLoginAt(now)
                .build());

        // 2. Cuentas
        Account acc1 = accountRepository.save(Account.builder()
                .id("ACC-000001")
                .userId(user1.getId())
                .name("Cuenta de Ahorros Principal")
                .type("SAVINGS")
                .currency("COP")
                .balance(BigDecimal.valueOf(5000000.00))
                .status("ACTIVE")
                .createdAt(now.minus(30, ChronoUnit.DAYS))
                .updatedAt(now)
                .build());

        Account acc2 = accountRepository.save(Account.builder()
                .id("ACC-000002")
                .userId(user2.getId())
                .name("Cuenta Corriente María")
                .type("CHECKING")
                .currency("COP")
                .balance(BigDecimal.valueOf(2500000.00))
                .status("ACTIVE")
                .createdAt(now.minus(20, ChronoUnit.DAYS))
                .updatedAt(now)
                .build());

        Account acc3 = accountRepository.save(Account.builder()
                .id("ACC-000003")
                .userId(user1.getId())
                .name("Billetera Dólares")
                .type("SAVINGS")
                .currency("USD")
                .balance(BigDecimal.valueOf(1500.00))
                .status("ACTIVE")
                .createdAt(now.minus(15, ChronoUnit.DAYS))
                .updatedAt(now)
                .build());

        // 3. Beneficiarios
        beneficiaryRepository.save(Beneficiary.builder()
                .id("BEN-000001")
                .userId(user1.getId())
                .name("Maria Gomez")
                .alias("María Amiga")
                .accountReference(acc2.getId())
                .status("ACTIVE")
                .createdAt(now.minus(10, ChronoUnit.DAYS))
                .updatedAt(now)
                .build());

        // 4. Población de 50 transacciones realistas para sorting y searching
        String[] categories = {"FOOD", "TRANSPORT", "SALARY", "SERVICES", "ENTERTAINMENT", "HEALTH", "SHOPPING"};
        String[] descriptions = {
                "Supermercado Éxito", "Restaurante Gourmet", "Pago Nómina Quincenal", "Uber viaje",
                "Factura de Energía", "Subscripción Netflix", "Farmacia Droguería", "Compra Amazon",
                "Gasolina Terpel", "Cafetería Juan Valdez", "Transferencia recibida", "Cine Colombia"
        };
        Random random = new Random(100);

        int totalTransactions = 50;
        for (int i = 1; i <= totalTransactions; i++) {
            String txId = String.format("TX-%06d", i);
            String type = (i % 5 == 0) ? "INCOME" : "EXPENSE";
            double amountVal = 15000.0 + (random.nextInt(300) * 1000.0);
            String category = categories[random.nextInt(categories.length)];
            String desc = descriptions[random.nextInt(descriptions.length)] + " #" + i;
            Instant txTime = now.minus(random.nextInt(25), ChronoUnit.DAYS).minus(random.nextInt(24), ChronoUnit.HOURS);

            transactionRepository.save(Transaction.builder()
                    .id(txId)
                    .accountId(acc1.getId())
                    .userId(user1.getId())
                    .type(type)
                    .category(category)
                    .amount(BigDecimal.valueOf(amountVal))
                    .currency("COP")
                    .description(desc)
                    .status("COMPLETED")
                    .timestamp(txTime)
                    .createdAt(txTime)
                    .build());
        }

        // 5. Ejecutar un benchmark inicial para poblar colecciones de benchmark
        BenchmarkRun run = benchmarkRepository.saveRun(BenchmarkRun.builder()
                .id("RUN-20260918-001")
                .framework("JMH")
                .javaVersion("17.0.20.1")
                .executedAt(now)
                .datasetSource("firebase-synthetic")
                .datasetSize(5000)
                .status("COMPLETED")
                .build());

        benchmarkRepository.saveResult(BenchmarkResult.builder()
                .id("RESULT-000001")
                .runId(run.getId())
                .algorithm("MERGE_SORT")
                .operation("SORT")
                .inputSize(5000)
                .score(184520.42)
                .scoreError(3210.55)
                .unit("ns/op")
                .build());

        benchmarkRepository.saveResult(BenchmarkResult.builder()
                .id("RESULT-000002")
                .runId(run.getId())
                .algorithm("QUICK_SORT")
                .operation("SORT")
                .inputSize(5000)
                .score(142100.15)
                .scoreError(2890.10)
                .unit("ns/op")
                .build());

        benchmarkRepository.saveResult(BenchmarkResult.builder()
                .id("RESULT-000003")
                .runId(run.getId())
                .algorithm("BINARY_SEARCH")
                .operation("SEARCH")
                .inputSize(5000)
                .score(823.10)
                .scoreError(45.20)
                .unit("ns/op")
                .build());

        log.info("Database seeding successfully finished! Seeded 2 users, 3 accounts, 1 beneficiary, 50 transactions, and 1 benchmark run.");

        return Map.of(
                "status", "SUCCESS",
                "message", "Database successfully seeded with realistic sample data",
                "usersCount", 2,
                "accountsCount", 3,
                "beneficiariesCount", 1,
                "transactionsCount", totalTransactions,
                "benchmarksRunId", run.getId()
        );
    }
}

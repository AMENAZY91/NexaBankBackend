package com.nexabank.api.service;

import com.nexabank.api.dto.user.UserResponse;
import com.nexabank.api.mapper.UserMapper;
import com.nexabank.api.model.AuditLog;
import com.nexabank.api.model.Transaction;
import com.nexabank.api.model.User;
import com.nexabank.api.repository.AuditLogRepository;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.TransactionRepository;
import com.nexabank.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserMapper userMapper;

    public AdminService(UserRepository userRepository,
                        TransactionRepository transactionRepository,
                        AccountRepository accountRepository,
                        AuditLogRepository auditLogRepository,
                        UserMapper userMapper) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
        this.userMapper = userMapper;
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        long totalUsers = userRepository.count();
        long totalTransactions = transactionRepository.count();
        long totalAccounts = accountRepository.count();
        long activeToday = userRepository.findAll().stream()
                .filter(u -> u.getLastLoginAt() != null && u.getLastLoginAt().isAfter(Instant.now().minusSeconds(86400)))
                .count();
        BigDecimal totalVolume = transactionRepository.findAll().stream()
                .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        metrics.put("totalUsers", totalUsers);
        metrics.put("totalTransactions", totalTransactions);
        metrics.put("totalAccounts", totalAccounts);
        metrics.put("activeToday", activeToday);
        metrics.put("totalVolume", totalVolume.doubleValue());
        metrics.put("generatedAt", Instant.now().toString());
        return metrics;
    }

    public List<UserResponse> getUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getAuditEvents() {
        return auditLogRepository.findAll().stream()
                .map(this::auditLogToMap)
                .sorted((a, b) -> ((Instant) b.get("createdAt")).compareTo((Instant) a.get("createdAt")))
                .limit(50)
                .collect(Collectors.toList());
    }

    private Map<String, Object> auditLogToMap(AuditLog a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.getId());
        map.put("userId", a.getUserId());
        map.put("action", a.getAction());
        map.put("resourceType", a.getResourceType());
        map.put("resourceId", a.getResourceId());
        map.put("status", a.getStatus());
        map.put("timestamp", a.getTimestamp());
        map.put("metadata", a.getMetadata());
        map.put("createdAt", a.getTimestamp());
        return map;
    }
}
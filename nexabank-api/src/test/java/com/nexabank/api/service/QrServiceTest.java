package com.nexabank.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexabank.api.dto.qr.QrPayRequest;
import com.nexabank.api.exception.InsufficientFundsException;
import com.nexabank.api.model.Account;
import com.nexabank.api.repository.AccountRepository;
import com.nexabank.api.repository.TransactionRepository;
import com.nexabank.api.security.SecurityUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QrServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private NotificationService notificationService;

    private QrService qrService;

    @BeforeEach
    void setUp() {
        qrService = new QrService(accountRepository, transactionRepository, securityUtils, notificationService);
    }

    @Test
    void processQrPayment_shouldUseAtomicBalanceDelta_notSaveOverwrite() {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        Account sourceAccount = Account.builder().id("ACC-000001").userId("USR-000001")
                .currency("COP").balance(new BigDecimal("100000.00")).build();
        when(accountRepository.findByUserId("USR-000001")).thenReturn(List.of(sourceAccount));
        when(accountRepository.applyBalanceDelta("ACC-000001", new BigDecimal("-25000.00")))
                .thenReturn(new BigDecimal("75000.00"));

        QrPayRequest request = QrPayRequest.builder()
                .qrData("nexabank:pay:ACC-999999:Tienda")
                .amount("25000.00")
                .currency("COP")
                .build();

        Map<String, Object> result = qrService.processQrPayment(request);

        assertThat(result.get("success")).isEqualTo(true);
        // Mejora #15: el débito se hace vía applyBalanceDelta (transacción Firestore atómica),
        // nunca con sourceAccount.setBalance(...) + accountRepository.save(...) (sobrescritura
        // no transaccional, vulnerable a lost update entre pagos QR concurrentes).
        verify(accountRepository).applyBalanceDelta("ACC-000001", new BigDecimal("-25000.00"));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository).save(any());
        verify(notificationService).notify(eq("USR-000001"), eq("QR_PAYMENT"), any(), any(), any(), any());
    }

    @Test
    void processQrPayment_shouldFail_whenAtomicDeltaThrowsInsufficientFunds() {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        Account sourceAccount = Account.builder().id("ACC-000001").userId("USR-000001")
                .currency("COP").balance(new BigDecimal("100.00")).build();
        when(accountRepository.findByUserId("USR-000001")).thenReturn(List.of(sourceAccount));
        when(accountRepository.applyBalanceDelta(eq("ACC-000001"), any()))
                .thenThrow(new InsufficientFundsException("Insufficient funds"));

        QrPayRequest request = QrPayRequest.builder()
                .qrData("nexabank:pay:ACC-999999:Tienda")
                .amount("25000.00")
                .currency("COP")
                .build();

        Map<String, Object> result = qrService.processQrPayment(request);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error")).isEqualTo("Insufficient funds");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void processQrPayment_shouldFail_whenNoSourceAccount() {
        when(securityUtils.getCurrentUserId()).thenReturn("USR-000001");
        when(accountRepository.findByUserId("USR-000001")).thenReturn(List.of());

        QrPayRequest request = QrPayRequest.builder()
                .qrData("nexabank:pay:ACC-999999:Tienda")
                .amount("25000.00")
                .build();

        Map<String, Object> result = qrService.processQrPayment(request);

        assertThat(result.get("success")).isEqualTo(false);
        verify(accountRepository, never()).applyBalanceDelta(any(), any());
    }
}
